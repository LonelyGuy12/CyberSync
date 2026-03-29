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


class QuizRequest(BaseModel):
    topic: str
    skill_level: str = "beginner"
    num_questions: int = Field(default=5, ge=3, le=10)


class QuizQuestion(BaseModel):
    question: str
    options: list[str]
    correct_answer: int  # index into options
    explanation: str


class QuizSubmission(BaseModel):
    user_id: str
    plan_id: str
    day: int
    answers: list[int]  # user's selected indices


class ReminderSettings(BaseModel):
    user_id: str
    enabled: bool = True
    hour: int = Field(default=9, ge=0, le=23)
    minute: int = Field(default=0, ge=0, le=59)


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

QUIZ_PROMPT_TEMPLATE = """You are a quiz generator for educational content.

Generate exactly {num_questions} multiple-choice questions about:
Topic: {topic}
Difficulty: {skill_level}

Return ONLY valid JSON matching this exact schema (no markdown, no explanation):
{{
  "questions": [
    {{
      "question": "<question text>",
      "options": ["<option A>", "<option B>", "<option C>", "<option D>"],
      "correct_answer": <0-3 index of correct option>,
      "explanation": "<brief explanation of why the answer is correct>"
    }}
  ]
}}

Rules:
- Exactly 4 options per question.
- correct_answer is a 0-based index.
- Questions should test understanding, not just memorization.
- Vary difficulty within the {skill_level} range.
"""

DIFFICULTY_UP_PROMPT = """The learner is performing exceptionally — they've completed {streak} days in a row without missing any.

Original goal: {goal}
Remaining schedule (uncompleted days):
{remaining_json}

Increase the difficulty of the remaining schedule. Make topics more advanced, add deeper sub-topics, and slightly increase the complexity.

Return ONLY valid JSON as a list of daily tasks matching this schema (no markdown):
[
  {{"day": <int>, "topic": "<more advanced sub-topic>", "duration_mins": <int>, "status": "pending"}}
]

Rules:
- Keep the same number of days.
- Increase conceptual depth by ~30%.
- Keep duration_mins the same or slightly higher.
- Make topic descriptions more specific and challenging.
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

    # Detect 7 consecutive completions → increase difficulty
    consecutive_completed = 0
    needs_difficulty_boost = False
    for task in sorted_schedule:
        if task["status"] == "completed":
            consecutive_completed += 1
            if consecutive_completed >= 7:
                needs_difficulty_boost = True
                break
        else:
            consecutive_completed = 0

    if needs_simplification:
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

        new_remaining = []
        for entry in new_schedule_raw:
            task = DailyTask(**entry)
            new_remaining.append(task.model_dump())

        completed = [t for t in sorted_schedule if t["status"] == "completed"]
        updated_schedule = completed + new_remaining

        doc_ref.update({
            "schedule": updated_schedule,
            "simplified_at": datetime.utcnow().isoformat(),
        })

    elif needs_difficulty_boost and not data.get("difficulty_boosted"):
        remaining = [t for t in sorted_schedule if t["status"] == "pending"]
        if not remaining:
            return

        prompt = DIFFICULTY_UP_PROMPT.format(
            streak=consecutive_completed,
            goal=data.get("goal", ""),
            remaining_json=json.dumps(remaining, indent=2),
        )

        response = client.chat.completions.create(
            model=LLM_MODEL,
            messages=[{"role": "user", "content": prompt}],
        )
        new_schedule_raw = parse_llm_json(response.choices[0].message.content)

        new_remaining = []
        for entry in new_schedule_raw:
            task = DailyTask(**entry)
            new_remaining.append(task.model_dump())

        completed = [t for t in sorted_schedule if t["status"] == "completed"]
        missed = [t for t in sorted_schedule if t["status"] == "missed"]
        updated_schedule = completed + missed + new_remaining

        doc_ref.update({
            "schedule": updated_schedule,
            "difficulty_boosted": True,
            "boosted_at": datetime.utcnow().isoformat(),
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
        day_statuses = {int(t.get('day', 0)): t.get('status', '?') for t in schedule}
        print(f"[update-status] day={req.day}, schedule_days={day_statuses}")
        raise HTTPException(
            status_code=400,
            detail=f"Day {req.day} not found or already updated. Current statuses: {day_statuses}"
        )

    doc_ref.update({"schedule": schedule})

    # ── Award points if completed ────────────────────────────────────────
    points_earned = 0
    total_points = 0
    streak = 0
    if req.status == "completed":
        points_earned = 10  # base points per day

        # Calculate streak bonus: count consecutive completed days ending at this day
        sorted_schedule = sorted(schedule, key=lambda t: int(t["day"]))
        current_streak = 0
        for t in sorted_schedule:
            if t["status"] == "completed":
                current_streak += 1
            else:
                current_streak = 0
        streak = current_streak

        # Streak bonuses
        if streak >= 7:
            points_earned += 25  # week streak bonus
        elif streak >= 3:
            points_earned += 10  # 3-day streak bonus

        # Update user points in Firestore
        points_ref = db.collection("user_points").document(req.user_id)
        points_doc = points_ref.get()
        if points_doc.exists:
            current = points_doc.to_dict().get("total_points", 0)
            total_points = current + points_earned
            points_ref.update({
                "total_points": total_points,
                "last_earned": points_earned,
                "streak": streak,
                "updated_at": datetime.utcnow().isoformat(),
            })
        else:
            total_points = points_earned
            points_ref.set({
                "user_id": req.user_id,
                "total_points": total_points,
                "last_earned": points_earned,
                "streak": streak,
                "updated_at": datetime.utcnow().isoformat(),
            })

    # Trigger background check for consecutive misses
    background_tasks.add_task(check_and_simplify, req.user_id, req.plan_id)

    return {
        "message": f"Day {req.day} marked as {req.status}",
        "points_earned": points_earned,
        "total_points": total_points,
        "streak": streak,
    }


@app.get("/points/{user_id}")
async def get_points(user_id: str):
    """Get a user's total points and streak."""
    doc = db.collection("user_points").document(user_id).get()
    if not doc.exists:
        return {"total_points": 0, "streak": 0, "last_earned": 0}
    data = doc.to_dict()
    return {
        "total_points": data.get("total_points", 0),
        "streak": data.get("streak", 0),
        "last_earned": data.get("last_earned", 0),
    }


# ── Profile / Achievements endpoint ──────────────────────────────────────────

TROPHY_DEFINITIONS = [
    {"id": "first_step",      "name": "First Step",       "desc": "Complete your first lesson",         "icon": "⭐", "requirement": 1},
    {"id": "getting_started", "name": "Getting Started",   "desc": "Complete 5 lessons",                "icon": "🌟", "requirement": 5},
    {"id": "dedicated",       "name": "Dedicated Learner", "desc": "Complete 10 lessons",               "icon": "📚", "requirement": 10},
    {"id": "scholar",         "name": "Scholar",           "desc": "Complete 25 lessons",               "icon": "🎓", "requirement": 25},
    {"id": "master",          "name": "Master",            "desc": "Complete 50 lessons",               "icon": "👑", "requirement": 50},
    {"id": "streak_3",        "name": "On Fire",           "desc": "Reach a 3-day streak",              "icon": "🔥", "requirement": 3,  "type": "streak"},
    {"id": "streak_7",        "name": "Week Warrior",      "desc": "Reach a 7-day streak",              "icon": "⚡", "requirement": 7,  "type": "streak"},
    {"id": "streak_14",       "name": "Unstoppable",       "desc": "Reach a 14-day streak",             "icon": "💎", "requirement": 14, "type": "streak"},
    {"id": "multi_topic",     "name": "Explorer",          "desc": "Study 3 different topics",          "icon": "🧭", "requirement": 3,  "type": "topics"},
    {"id": "points_100",      "name": "Century Club",      "desc": "Earn 100 total XP",                 "icon": "💯", "requirement": 100, "type": "points"},
    {"id": "points_500",      "name": "XP Hunter",         "desc": "Earn 500 total XP",                 "icon": "🏆", "requirement": 500, "type": "points"},
]


@app.get("/profile/{user_id}")
async def get_profile(user_id: str):
    """Get full user profile with stats, achievements, and trophies."""
    # Gather points data
    points_doc = db.collection("user_points").document(user_id).get()
    points_data = points_doc.to_dict() if points_doc.exists else {}
    total_points = points_data.get("total_points", 0)
    current_streak = points_data.get("streak", 0)
    best_streak = points_data.get("best_streak", current_streak)

    # Gather plan stats
    plans = db.collection("learning_plans").where("user_id", "==", user_id).stream()
    total_completed = 0
    total_missed = 0
    total_pending = 0
    total_plans = 0
    topics_studied = set()

    for doc in plans:
        d = doc.to_dict()
        total_plans += 1
        topic = d.get("topic", "")
        if topic:
            topics_studied.add(topic.lower())
        for task in d.get("schedule", []):
            s = task.get("status", "pending")
            if s == "completed":
                total_completed += 1
            elif s == "missed":
                total_missed += 1
            else:
                total_pending += 1

    # Calculate stars (1 star per 5 completed lessons)
    stars = total_completed // 5

    # Determine which trophies are unlocked
    unlocked_trophies = []
    for t in TROPHY_DEFINITIONS:
        trophy_type = t.get("type", "lessons")
        earned = False
        if trophy_type == "streak":
            earned = best_streak >= t["requirement"]
        elif trophy_type == "topics":
            earned = len(topics_studied) >= t["requirement"]
        elif trophy_type == "points":
            earned = total_points >= t["requirement"]
        else:  # lessons
            earned = total_completed >= t["requirement"]

        unlocked_trophies.append({
            "id": t["id"],
            "name": t["name"],
            "desc": t["desc"],
            "icon": t["icon"],
            "unlocked": earned,
        })

    # Update best_streak if current is higher
    if current_streak > best_streak:
        best_streak = current_streak
        db.collection("user_points").document(user_id).set(
            {"best_streak": best_streak}, merge=True
        )

    return {
        "user_id": user_id,
        "total_points": total_points,
        "stars": stars,
        "current_streak": current_streak,
        "best_streak": best_streak,
        "total_completed": total_completed,
        "total_missed": total_missed,
        "total_pending": total_pending,
        "total_plans": total_plans,
        "topics_studied": list(topics_studied),
        "trophies": unlocked_trophies,
    }


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


# ── Quiz endpoints ───────────────────────────────────────────────────────────

@app.post("/generate-quiz")
async def generate_quiz(req: QuizRequest):
    """Generate AI quiz questions for a topic."""
    prompt = QUIZ_PROMPT_TEMPLATE.format(
        topic=req.topic,
        skill_level=req.skill_level,
        num_questions=req.num_questions,
    )
    response = client.chat.completions.create(
        model=LLM_MODEL,
        messages=[{"role": "user", "content": prompt}],
    )
    quiz_data = parse_llm_json(response.choices[0].message.content)
    questions = quiz_data.get("questions", quiz_data) if isinstance(quiz_data, dict) else quiz_data
    # Validate
    validated = []
    for q in questions:
        validated.append(QuizQuestion(**q).model_dump())
    return {"topic": req.topic, "questions": validated}


@app.post("/submit-quiz")
async def submit_quiz(req: QuizSubmission):
    """Submit quiz answers, calculate score, award bonus XP."""
    doc = db.collection("learning_plans").document(req.plan_id).get()
    if not doc.exists:
        raise HTTPException(status_code=404, detail="Plan not found")

    # Find the day's topic to fetch from quiz cache or regenerate
    data = doc.to_dict()
    schedule = data.get("schedule", [])
    day_task = next((t for t in schedule if int(t["day"]) == req.day), None)
    if not day_task:
        raise HTTPException(status_code=400, detail="Day not found")

    # Score calculation (we trust the client sends correct number of answers)
    # Award bonus XP based on score
    correct_count = len([a for a in req.answers if a >= 0])  # placeholder
    score_percent = (correct_count / max(len(req.answers), 1)) * 100

    # Award bonus XP for quiz completion
    bonus_xp = 5  # base quiz completion bonus
    if score_percent >= 80:
        bonus_xp = 15  # excellence bonus
    elif score_percent >= 60:
        bonus_xp = 10  # good score bonus

    # Update user points
    points_ref = db.collection("user_points").document(req.user_id)
    points_doc = points_ref.get()
    if points_doc.exists:
        current = points_doc.to_dict().get("total_points", 0)
        points_ref.update({
            "total_points": current + bonus_xp,
            "updated_at": datetime.utcnow().isoformat(),
        })
        total = current + bonus_xp
    else:
        points_ref.set({
            "user_id": req.user_id,
            "total_points": bonus_xp,
            "updated_at": datetime.utcnow().isoformat(),
        })
        total = bonus_xp

    return {
        "score_percent": score_percent,
        "bonus_xp": bonus_xp,
        "total_points": total,
        "message": f"Quiz completed! +{bonus_xp} XP"
    }


# ── Leaderboard endpoint ────────────────────────────────────────────────────

@app.get("/leaderboard")
async def get_leaderboard(limit: int = 20):
    """Get top users by XP."""
    docs = db.collection("user_points").stream()
    users = []
    for doc in docs:
        d = doc.to_dict()
        user_id = d.get("user_id", doc.id)

        # Try to get display name from Firebase Auth
        display_name = None
        try:
            from firebase_admin import auth
            user_record = auth.get_user(user_id)
            display_name = user_record.display_name or user_record.email
        except Exception:
            pass

        if not display_name:
            display_name = f"Learner_{user_id[:6]}"

        users.append({
            "user_id": user_id,
            "display_name": display_name,
            "total_points": d.get("total_points", 0),
            "streak": d.get("streak", 0),
            "best_streak": d.get("best_streak", 0),
        })

    # Sort by points descending
    users.sort(key=lambda u: u["total_points"], reverse=True)
    return {"leaderboard": users[:limit]}


# ── Analytics endpoint ───────────────────────────────────────────────────────

@app.get("/analytics/{user_id}")
async def get_analytics(user_id: str):
    """Get learning analytics — daily completion rates, points history, etc."""
    plans = db.collection("learning_plans").where("user_id", "==", user_id).stream()

    daily_stats = []  # list of {day, status, topic, plan_topic}
    plan_summaries = []
    total_minutes_studied = 0

    for doc in plans:
        d = doc.to_dict()
        plan_topic = d.get("topic", "Unknown")
        schedule = d.get("schedule", [])
        completed_count = 0
        missed_count = 0

        for task in schedule:
            status = task.get("status", "pending")
            if status == "completed":
                completed_count += 1
                total_minutes_studied += task.get("duration_mins", 0)
            elif status == "missed":
                missed_count += 1

            daily_stats.append({
                "day": task.get("day", 0),
                "status": status,
                "topic": task.get("topic", ""),
                "plan_topic": plan_topic,
                "duration_mins": task.get("duration_mins", 0),
            })

        plan_summaries.append({
            "topic": plan_topic,
            "total_days": len(schedule),
            "completed": completed_count,
            "missed": missed_count,
            "pending": len(schedule) - completed_count - missed_count,
            "completion_rate": round(completed_count / max(len(schedule), 1) * 100, 1),
        })

    # Overall stats
    total_tasks = len(daily_stats)
    total_completed = sum(1 for d in daily_stats if d["status"] == "completed")
    total_missed = sum(1 for d in daily_stats if d["status"] == "missed")

    return {
        "total_tasks": total_tasks,
        "total_completed": total_completed,
        "total_missed": total_missed,
        "total_pending": total_tasks - total_completed - total_missed,
        "completion_rate": round(total_completed / max(total_tasks, 1) * 100, 1),
        "total_minutes_studied": total_minutes_studied,
        "plan_summaries": plan_summaries,
        "daily_stats": daily_stats,
    }


# ── Reminder settings endpoint ──────────────────────────────────────────────

@app.post("/reminder-settings")
async def save_reminder_settings(req: ReminderSettings):
    """Save user's reminder preferences."""
    db.collection("reminder_settings").document(req.user_id).set({
        "user_id": req.user_id,
        "enabled": req.enabled,
        "hour": req.hour,
        "minute": req.minute,
        "updated_at": datetime.utcnow().isoformat(),
    })
    return {"message": "Reminder settings saved"}


@app.get("/reminder-settings/{user_id}")
async def get_reminder_settings(user_id: str):
    doc = db.collection("reminder_settings").document(user_id).get()
    if not doc.exists:
        return {"enabled": True, "hour": 9, "minute": 0}
    d = doc.to_dict()
    return {"enabled": d.get("enabled", True), "hour": d.get("hour", 9), "minute": d.get("minute", 0)}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
