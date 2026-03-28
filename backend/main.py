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
    skill_level: str = Field(pattern="^(beginner|intermediate|advanced)$")


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


# ── Helpers ──────────────────────────────────────────────────────────────────

PLAN_PROMPT_TEMPLATE = """You are a study-plan generator. Create a structured learning plan.

Topic: {topic}
Daily available time: {daily_minutes} minutes
Current skill level: {skill_level}

Return ONLY valid JSON matching this exact schema (no markdown, no explanation):
{{
  "goal": "<one-sentence learning goal>",
  "total_days": <int>,
  "schedule": [
    {{"day": 1, "topic": "<sub-topic>", "duration_mins": <int>, "status": "pending"}},
    ...
  ]
}}

Rules:
- Each day's duration_mins must be <= {daily_minutes}.
- Provide between 7 and 30 days of content.
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
    prompt = PLAN_PROMPT_TEMPLATE.format(
        topic=req.topic,
        daily_minutes=req.daily_minutes,
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
        "created_at": datetime.utcnow().isoformat(),
        **plan.model_dump(),
    }
    doc_ref = db.collection("learning_plans").add(doc_data)
    plan_id = doc_ref[1].id

    return {"plan_id": plan_id, **plan.model_dump()}


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


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
