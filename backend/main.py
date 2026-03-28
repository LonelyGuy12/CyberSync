import os
import json
from datetime import datetime
from typing import Optional

import firebase_admin
from firebase_admin import credentials, firestore
from fastapi import FastAPI, HTTPException, BackgroundTasks
from pydantic import BaseModel, Field
from openai import OpenAI
from dotenv import load_dotenv

load_dotenv()

# ── Firebase Admin Init ──────────────────────────────────────────────────────
if not firebase_admin._apps:
    # Support JSON string via env var (for Docker/HF Spaces) or file path (local dev)
    firebase_json = os.getenv("FIREBASE_SERVICE_ACCOUNT_KEY_JSON")
    if firebase_json:
        cred = credentials.Certificate(json.loads(firebase_json))
    else:
        cred = credentials.Certificate(os.getenv("FIREBASE_SERVICE_ACCOUNT_KEY_PATH", "serviceAccountKey.json"))
    firebase_admin.initialize_app(cred)
db = firestore.client()

# ── OpenRouter LLM Init ──────────────────────────────────────────────────────
client = OpenAI(
    base_url="https://openrouter.ai/api/v1",
    api_key=os.getenv("OPENROUTER_API_KEY"),
)
LLM_MODEL = os.getenv("OPENROUTER_MODEL", "google/gemini-2.0-flash-001")

app = FastAPI(title="lonelytrack", version="0.1.0")


# ── Pydantic Models ─────────────────────────────────────────────────────────
class UserRequest(BaseModel):
    user_id: str
    topic: str
    daily_minutes: int = Field(gt=0, le=480)
    total_days: int = Field(gt=0, le=365, default=14)
    skill_level: str = Field(pattern="^(beginner|intermediate|advanced|pro|Beginner|Intermediate|Advanced|Pro)$")


class DailyTask(BaseModel):
    day: int
    topic: str
    duration_mins: int
    status: str = "pending"  # pending | completed | missed


class LearningPlan(BaseModel):
    goal: str
    total_days: int
    schedule: list[DailyTask]


class StatusUpdate(BaseModel):
    user_id: str
    plan_id: str
    day: int
    status: str = Field(pattern="^(completed|missed)$")


class TutorialRequest(BaseModel):
    topic: str
    skill_level: str = "beginner"


# ── Helpers ──────────────────────────────────────────────────────────────────

PLAN_PROMPT_TEMPLATE = """You are a study-plan generator. Create a structured learning plan.

Topic: {topic}
Daily available time: {daily_minutes} minutes
Total duration: {total_days} days
Current skill level: {skill_level}

Return ONLY valid JSON matching this exact schema (no markdown, no explanation):
{{
  "goal": "<one-sentence learning goal>",
  "total_days": {total_days},
  "schedule": [
    {{"day": 1, "topic": "<sub-topic>", "duration_mins": <int>, "status": "pending"}},
    ...
  ]
}}

Rules:
- Each day's duration_mins must be <= {daily_minutes}.
- Provide exactly {total_days} days of content.
- Tailor complexity to the {skill_level} level.
"""

SIMPLIFY_PROMPT_TEMPLATE = """The learner has missed 3 consecutive days. Simplify the remaining schedule.

Original goal: {goal}
Remaining schedule (days not yet completed):
{remaining_json}

Return ONLY valid JSON as a list of daily tasks matching this schema (no markdown):
[
  {{"day": <int starting from 1>, "topic": "<simplified sub-topic>", "duration_mins": <int>, "status": "pending"}}
]

Rules:
- Reduce complexity and session length by ~25%.
- Merge or drop low-priority topics.
- Keep the list between 3 and 20 entries.
"""

TUTORIAL_PROMPT_TEMPLATE = """You are an expert tutor. Write a comprehensive tutorial on the following topic.

Topic: {topic}
Skill level: {skill_level}

Write a well-structured tutorial with:
- A brief introduction explaining what this topic is and why it matters
- Clear step-by-step explanations with examples
- Code examples if applicable (use proper formatting)
- Key takeaways or summary at the end
- Practice exercises or questions to reinforce learning

Write in plain text with clear section headers (use ALL CAPS for headers).
Keep the tutorial focused and around 800-1200 words.
Make it engaging and easy to follow for a {skill_level} learner.
"""


def parse_llm_json(text: str):
    """Strip markdown fences and parse JSON from LLM output."""
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.split("\n", 1)[1] if "\n" in cleaned else cleaned[3:]
        if cleaned.endswith("```"):
            cleaned = cleaned[:-3]
    return json.loads(cleaned.strip())


# ── Background task: detect 3 consecutive misses → simplify ─────────────────

async def check_and_simplify(user_id: str, plan_id: str):
    doc_ref = db.collection("learning_plans").document(plan_id)
    doc = doc_ref.get()
    if not doc.exists:
        return

    data = doc.to_dict()
    schedule = data.get("schedule", [])
    sorted_schedule = sorted(schedule, key=lambda t: t["day"])

    # Detect 3 consecutive misses
    consecutive_misses = 0
    needs_simplification = False
    for task in sorted_schedule:
        if task["status"] == "missed":
            consecutive_misses += 1
            if consecutive_misses >= 3:
                needs_simplification = True
                break
        else:
            consecutive_misses = 0

    if not needs_simplification:
        return

    # Gather remaining (non-completed) tasks
    remaining = [t for t in sorted_schedule if t["status"] != "completed"]
    if not remaining:
        return

    prompt = SIMPLIFY_PROMPT_TEMPLATE.format(
        goal=data.get("goal", ""),
        remaining_json=json.dumps(remaining, indent=2),
    )

    response = client.chat.completions.create(
        model=LLM_MODEL,
        messages=[{"role": "user", "content": prompt}],
    )
    new_schedule_raw = parse_llm_json(response.choices[0].message.content)

    # Validate each entry
    new_remaining = []
    for entry in new_schedule_raw:
        task = DailyTask(**entry)
        new_remaining.append(task.model_dump())

    # Merge: keep completed tasks, replace the rest
    completed = [t for t in sorted_schedule if t["status"] == "completed"]
    updated_schedule = completed + new_remaining

    doc_ref.update({
        "schedule": updated_schedule,
        "simplified_at": datetime.utcnow().isoformat(),
    })


# ── Endpoints ────────────────────────────────────────────────────────────────

@app.post("/generate-plan")
async def generate_plan(req: UserRequest):
    # ── Check cache: reuse existing plan for same user + topic ───────────
    existing = (
        db.collection("learning_plans")
        .where("user_id", "==", req.user_id)
        .where("topic", "==", req.topic)
        .stream()
    )
    for doc in existing:
        d = doc.to_dict()
        plan_id = doc.id
        print(f"[generate-plan] Cache hit: returning existing plan {plan_id} for topic '{req.topic}'")
        return {
            "plan_id": plan_id,
            "goal": d.get("goal", ""),
            "total_days": d.get("total_days", 0),
            "schedule": d.get("schedule", []),
            "cached": True,
        }

    # ── No cache — generate new plan via LLM ─────────────────────────────
    prompt = PLAN_PROMPT_TEMPLATE.format(
        topic=req.topic,
        daily_minutes=req.daily_minutes,
        total_days=req.total_days,
        skill_level=req.skill_level,
    )

    response = client.chat.completions.create(
        model=LLM_MODEL,
        messages=[{"role": "user", "content": prompt}],
    )
    plan_data = parse_llm_json(response.choices[0].message.content)

    # Validate against Pydantic model
    plan = LearningPlan(**plan_data)

    # Persist to Firestore
    doc_data = {
        "user_id": req.user_id,
        "topic": req.topic,
        "created_at": datetime.utcnow().isoformat(),
        **plan.model_dump(),
    }
    doc_ref = db.collection("learning_plans").add(doc_data)
    plan_id = doc_ref[1].id

    return {"plan_id": plan_id, **plan.model_dump(), "cached": False}


@app.post("/update-status")
async def update_status(req: StatusUpdate, background_tasks: BackgroundTasks):
    doc_ref = db.collection("learning_plans").document(req.plan_id)
    doc = doc_ref.get()

    if not doc.exists:
        raise HTTPException(status_code=404, detail="Plan not found")

    data = doc.to_dict()
    if data.get("user_id") != req.user_id:
        raise HTTPException(status_code=403, detail="Not your plan")

    schedule = data.get("schedule", [])
    updated = False
    for task in schedule:
        if int(task["day"]) == req.day and task["status"] == "pending":
            task["status"] = req.status
            updated = True
            break

    if not updated:
        # Log debug info to help diagnose
        day_statuses = {int(t.get('day', 0)): t.get('status', '?') for t in schedule}
        print(f"[update-status] day={req.day}, schedule_days={day_statuses}")
        raise HTTPException(
            status_code=400,
            detail=f"Day {req.day} not found or already updated. Current statuses: {day_statuses}"
        )

    doc_ref.update({"schedule": schedule})

    # Trigger background check for consecutive misses
    background_tasks.add_task(check_and_simplify, req.user_id, req.plan_id)

    return {"message": f"Day {req.day} marked as {req.status}"}


@app.get("/plan/{plan_id}")
async def get_plan(plan_id: str):
    doc = db.collection("learning_plans").document(plan_id).get()
    if not doc.exists:
        raise HTTPException(status_code=404, detail="Plan not found")
    return {"plan_id": plan_id, **doc.to_dict()}


@app.post("/generate-tutorial")
async def generate_tutorial(req: TutorialRequest):
    prompt = TUTORIAL_PROMPT_TEMPLATE.format(
        topic=req.topic,
        skill_level=req.skill_level,
    )

    response = client.chat.completions.create(
        model=LLM_MODEL,
        messages=[{"role": "user", "content": prompt}],
    )
    content = response.choices[0].message.content.strip()
    return {"topic": req.topic, "tutorial": content}


# ── History endpoint ──────────────────────────────────────────────────────────
@app.get("/history/{user_id}")
async def get_history(user_id: str):
    """Return all learning plans for a user, newest first."""
    try:
        docs = (
            db.collection("learning_plans")
            .where("user_id", "==", user_id)
            .stream()
        )
        plans = []
        for doc in docs:
            d = doc.to_dict()
            schedule = d.get("schedule", [])
            completed = sum(1 for t in schedule if t.get("status") == "completed")
            topic = d.get("topic", "") or ""
            # Fallback: extract topic from goal if topic wasn't saved
            if not topic and d.get("goal"):
                topic = d["goal"].split(".")[0][:50]
            plans.append({
                "plan_id": doc.id,
                "goal": d.get("goal", ""),
                "topic": topic,
                "total_days": d.get("total_days", len(schedule)),
                "completed_days": completed,
                "created_at": d.get("created_at", ""),
            })
        # Sort newest first in Python (avoids needing a Firestore composite index)
        plans.sort(key=lambda p: p["created_at"], reverse=True)
        return {"plans": plans}
    except Exception as e:
        print(f"[history] Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ── Delete plan endpoint ─────────────────────────────────────────────────────
@app.delete("/plan/{plan_id}")
async def delete_plan(plan_id: str, user_id: str):
    """Delete a learning plan. Requires user_id as query param for ownership check."""
    doc_ref = db.collection("learning_plans").document(plan_id)
    doc = doc_ref.get()
    if not doc.exists:
        raise HTTPException(status_code=404, detail="Plan not found")
    data = doc.to_dict()
    if data.get("user_id") != user_id:
        raise HTTPException(status_code=403, detail="Not your plan")
    doc_ref.delete()
    return {"message": "Plan deleted"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
