# LonelyTrack (CyberSync)

**AI-powered learning consistency agent** that generates personalized study plans, tracks daily progress, adapts difficulty based on performance, quizzes you on completed lessons, gamifies learning with XP/trophies/streaks, and sends daily reminders — all powered by LLMs and Firebase.

Built at the **AI Agent Hackathon by The Product Space**.

---

## Table of Contents

- [Architecture](#architecture)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Backend (FastAPI)](#backend-fastapi)
  - [Endpoints](#endpoints)
  - [Pydantic Models](#pydantic-models)
  - [Background Intelligence](#background-intelligence)
  - [Environment Variables](#environment-variables)
  - [Dependencies](#backend-dependencies)
  - [Running Locally](#running-the-backend)
  - [Hosted Deployment](#hosted-deployment)
- [Android App (Kotlin)](#android-app-kotlin)
  - [Screens](#screens)
  - [Project Structure](#project-structure)
  - [Authentication](#authentication)
  - [API Layer](#api-layer)
  - [Gamification System](#gamification-system)
  - [Navigation Drawer](#navigation-drawer)
  - [Dependencies](#android-dependencies)
  - [Running the App](#running-the-app)
- [Firebase Setup](#firebase-setup)
- [License](#license)

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                        ANDROID APP (Kotlin)                         │
│                                                                      │
│  LoginActivity ─► RegisterActivity       ┌── AnalyticsActivity      │
│       │                                   ├── LeaderboardActivity    │
│       ▼                                   ├── ProfileActivity        │
│  MainActivity ◄──────────────────────────►├── HistoryActivity        │
│  (Plan Form + Schedule List + XP Badge)   ├── TutorialActivity       │
│       │                                   └── QuizActivity           │
│       │  Firestore SDK (real-time snapshots)                        │
└───────┼──────────────────────────────────────────────────────────────┘
        │                    │
        │ HTTPS/REST         │ Real-time Listener
        ▼                    ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     FIREBASE FIRESTORE                               │
│                                                                      │
│  Collections:  learning_plans  │  user_points  │  reminder_settings  │
└──────────────────────────────────────────────────────────────────────┘
        ▲
        │ Firebase Admin SDK
        │
┌──────────────────────────────────────────────────────────────────────┐
│                    FASTAPI BACKEND (Python)                           │
│                                                                      │
│  /generate-plan     /update-status      /generate-tutorial           │
│  /generate-quiz     /submit-quiz        /plan/{id}                   │
│  /history/{uid}     /delete /plan/{id}  /points/{uid}                │
│  /profile/{uid}     /analytics/{uid}    /leaderboard                 │
│  /reminder-settings                                                  │
│                                                                      │
│  Background Tasks:                                                   │
│    • 3 missed days → AI simplifies schedule                          │
│    • 7 completed days → AI increases difficulty                      │
└──────────────────────┬───────────────────────────────────────────────┘
                       │
                       │ OpenAI-compatible SDK
                       ▼
              ┌─────────────────┐
              │   OpenRouter     │
              │   (LLM Gateway)  │
              │                  │
              │  Gemini 2.0 Flash│
              │  Claude / Llama  │
              │  Any model       │
              └─────────────────┘
```

---

## Features

### Core Learning Agent
| Feature | Description |
|---|---|
| **AI Plan Generation** | LLM creates a structured day-by-day study schedule tailored to topic, time budget, skill level, and duration |
| **Plan Caching** | Same topic + user → returns existing plan instantly (no duplicate LLM calls) |
| **Daily Task Tracking** | Mark each day as completed or missed with optimistic UI updates |
| **On-Demand Tutorials** | Tap any day to get a full AI-generated tutorial (800–1200 words with examples) |
| **AI Quizzes** | 5-question AI-generated quiz after completing a lesson — earn bonus XP based on score |
| **Real-Time Sync** | Android listens to Firestore snapshots — schedule changes appear instantly |
| **Session Persistence** | App remembers your last plan across restarts via SharedPreferences |

### Adaptive Intelligence (Background Tasks)
| Feature | Description |
|---|---|
| **Auto-Simplification** | 3 consecutive missed days → AI rewrites remaining schedule ~25% lighter |
| **Difficulty Boost** | 7 consecutive completed days → AI increases remaining schedule difficulty |

### Gamification
| Feature | Description |
|---|---|
| **XP Points** | +10 XP per completed task, +10 bonus at 3-day streak, +25 bonus at 7-day streak |
| **Quiz Bonus XP** | +5 to +15 XP based on quiz score |
| **Stars** | 1 star per 5 completed lessons |
| **Trophies (11)** | ⭐ First Step, 🌟 Getting Started, 📚 Dedicated Learner, 🎓 Scholar, 👑 Master, 🔥 On Fire, ⚡ Week Warrior, 💎 Unstoppable, 🧭 Explorer, 💯 Century Club, 🏆 XP Hunter |
| **Leaderboard** | Global ranking by XP with gold/silver/bronze medals |
| **Streak Tracking** | Current streak + best streak persisted in Firestore |

### User Management
| Feature | Description |
|---|---|
| **Email/Password Auth** | Firebase Authentication for registered accounts |
| **Anonymous Auth** | "Continue as Guest" for instant access |
| **User Profile** | Stats dashboard: XP, stars, streaks, trophies, topics studied |
| **Analytics** | Completion rate, minutes studied, per-course progress breakdown |
| **Plan History** | Browse and reload all past learning plans, delete unwanted ones |
| **Daily Reminders** | WorkManager-based notifications: "Don't break your streak!" |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Python 3, FastAPI, Uvicorn, Pydantic |
| LLM | OpenRouter API (default: `google/gemini-2.0-flash-001`) |
| Database | Firebase Firestore (3 collections) |
| Auth | Firebase Authentication (Email/Password + Anonymous) |
| Android | Kotlin, MVVM, ViewBinding, Retrofit 2, OkHttp, Coroutines, LiveData |
| Notifications | Android WorkManager |
| Hosting | HuggingFace Spaces (Docker) |
| Build | Gradle 8.11.1, AGP 8.7.3, Kotlin 2.0.21 |

---

## Backend (FastAPI)

### Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/generate-plan` | Generate AI study plan (or return cached) |
| `POST` | `/update-status` | Mark day completed/missed, award XP, trigger background checks |
| `GET` | `/plan/{plan_id}` | Retrieve a single plan |
| `DELETE` | `/plan/{plan_id}?user_id=` | Delete a plan (ownership verified) |
| `POST` | `/generate-tutorial` | AI tutorial for a topic (800-1200 words) |
| `POST` | `/generate-quiz` | AI multiple-choice quiz (3-10 questions) |
| `POST` | `/submit-quiz` | Grade quiz answers, award bonus XP |
| `GET` | `/history/{user_id}` | All plans for a user (newest first) |
| `GET` | `/points/{user_id}` | Total XP, current streak, best streak |
| `GET` | `/profile/{user_id}` | Full profile: stats, trophies, topics |
| `GET` | `/analytics/{user_id}` | Completion rate, minutes studied, per-course breakdown |
| `GET` | `/leaderboard` | Top users by XP (with medal emojis) |
| `POST` | `/reminder-settings` | Save daily reminder preferences |
| `GET` | `/reminder-settings/{user_id}` | Retrieve reminder preferences |

### Pydantic Models

| Model | Fields |
|---|---|
| `UserRequest` | `user_id`, `topic`, `daily_minutes` (1–480), `total_days` (1–365), `skill_level` (beginner/intermediate/advanced/pro) |
| `DailyTask` | `day`, `topic`, `duration_mins`, `status` (pending/completed/missed) |
| `LearningPlan` | `goal`, `total_days`, `schedule: list[DailyTask]` |
| `StatusUpdate` | `user_id`, `plan_id`, `day`, `status` (completed/missed) |
| `TutorialRequest` | `topic`, `skill_level` |
| `QuizRequest` | `topic`, `skill_level`, `num_questions` (3–10, default 5) |
| `QuizQuestion` | `question`, `options: list[str]`, `correct_answer: int`, `explanation` |
| `QuizSubmission` | `user_id`, `plan_id`, `day`, `answers: list[int]` |
| `ReminderSettings` | `user_id`, `enabled`, `hour` (0–23), `minute` (0–59) |

### Background Intelligence

After every `/update-status` call, a background task runs:

**Auto-Simplification (3 consecutive misses):**
1. Scans schedule for 3+ consecutive `"missed"` days
2. Sends remaining tasks to LLM: "simplify by ~25%"
3. Replaces pending tasks in Firestore, preserves completed ones
4. Adds `simplified_at` timestamp

**Difficulty Boost (7 consecutive completions):**
1. Scans for 7+ consecutive `"completed"` days
2. Sends remaining tasks to LLM: "increase difficulty, add advanced concepts"
3. Replaces pending tasks in Firestore
4. Adds `difficulty_increased_at` timestamp

### Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `FIREBASE_SERVICE_ACCOUNT_KEY_PATH` | Yes* | `serviceAccountKey.json` | Path to Firebase JSON key |
| `FIREBASE_SERVICE_ACCOUNT_KEY_JSON` | Yes* | — | Full JSON string (for Docker/HF Spaces) |
| `OPENROUTER_API_KEY` | Yes | — | API key from [openrouter.ai/keys](https://openrouter.ai/keys) |
| `OPENROUTER_MODEL` | No | `google/gemini-2.0-flash-001` | Any OpenRouter model ID |

*One of `_PATH` or `_JSON` is required. `_JSON` takes precedence (used in Docker deployments).

### Backend Dependencies

| Package | Purpose |
|---|---|
| `fastapi` | Web framework |
| `uvicorn[standard]` | ASGI server |
| `pydantic` | Request/response validation |
| `firebase-admin` | Firestore + Auth (server-side) |
| `openai` | OpenRouter LLM calls (OpenAI-compatible) |
| `python-dotenv` | Environment variable loading |

### Running the Backend

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # fill in OPENROUTER_API_KEY
# place serviceAccountKey.json in backend/
python main.py
```

Server starts at `http://0.0.0.0:8000`. Docs at `http://localhost:8000/docs`.

### Hosted Deployment

The backend is deployed on **HuggingFace Spaces** via Docker:

**Live API:** `https://e5k7-cybersync.hf.space/`

HF Spaces config:
- `Dockerfile` in `hf-spaces/` folder
- Secrets: `OPENROUTER_API_KEY` + `FIREBASE_SERVICE_ACCOUNT_KEY_JSON`
- Port 7860 (HF default)

---

## Android App (Kotlin)

**Package:** `com.lonelytrack` · **Min SDK:** 26 · **Target SDK:** 35 · **Version:** 1.0.0

### Screens

| Activity | Purpose |
|---|---|
| `LoginActivity` | Email/password login + "Continue as Guest" (Firebase anonymous auth) |
| `RegisterActivity` | New account registration with email/password |
| `MainActivity` | Plan form, schedule list with ✓/✗ buttons, XP badge, drawer navigation |
| `TutorialActivity` | Full AI-generated tutorial viewer with loading/error/retry states |
| `QuizActivity` | 5-question AI quiz with radio buttons, score display, XP award |
| `HistoryActivity` | Past plans list with progress bars, delete buttons, tap to reload |
| `ProfileActivity` | Stats dashboard: XP, stars, streaks, trophies grid, topics studied |
| `AnalyticsActivity` | Completion %, minutes studied, completed/missed/pending cards, per-course bars |
| `LeaderboardActivity` | Global XP ranking with 🥇🥈🥉 medals, current user highlighted |

### Project Structure

```
android/app/src/main/java/com/lonelytrack/
├── LoginActivity.kt
├── RegisterActivity.kt
├── MainActivity.kt
├── TutorialActivity.kt
├── QuizActivity.kt
├── HistoryActivity.kt
├── ProfileActivity.kt
├── AnalyticsActivity.kt
├── LeaderboardActivity.kt
├── adapter/
│   ├── ScheduleAdapter.kt          # Day cards with ✓/✗ or status labels
│   ├── HistoryAdapter.kt           # History cards with delete + progress
│   ├── TrophyAdapter.kt            # Trophy grid (locked/unlocked)
│   └── LeaderboardAdapter.kt       # Ranked user list
├── api/
│   ├── LearningApiService.kt       # Retrofit interface (14 endpoints)
│   └── RetrofitClient.kt           # OkHttp + Retrofit singleton
├── model/
│   └── Models.kt                   # All data classes
├── notification/
│   └── ReminderWorker.kt           # WorkManager daily notification
└── viewmodel/
    └── LearningViewModel.kt        # MVVM ViewModel with LiveData
```

### Authentication

- **Firebase Auth** with Email/Password and Anonymous providers
- Login screen is the launcher activity
- Guest users get a temporary Firebase UID (plans still persist in Firestore)
- Logout clears auth state + SharedPreferences, returns to login
- Drawer header shows user email or "Signed in as Guest"

### API Layer

**`LearningApiService`** — 14 Retrofit endpoints:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `generate-plan` | Create/cache study plan |
| `POST` | `update-status` | Mark day + earn XP |
| `GET` | `plan/{id}` | Fetch plan details |
| `DELETE` | `plan/{id}` | Delete a plan |
| `POST` | `generate-tutorial` | AI tutorial |
| `POST` | `generate-quiz` | AI quiz questions |
| `POST` | `submit-quiz` | Grade + XP |
| `GET` | `history/{uid}` | Plan history |
| `GET` | `points/{uid}` | XP + streak |
| `GET` | `profile/{uid}` | Full profile |
| `GET` | `analytics/{uid}` | Stats breakdown |
| `GET` | `leaderboard` | Global rankings |
| `POST` | `reminder-settings` | Save prefs |
| `GET` | `reminder-settings/{uid}` | Load prefs |

### Gamification System

**XP Points:**
- +10 per completed day
- +10 bonus for 3-day streak
- +25 bonus for 7-day streak
- +5 to +15 quiz bonus (score-based)
- Gold XP badge in the header with floating "+N XP" animation

**Trophies (11 unlockable):**
| Trophy | Requirement |
|---|---|
| ⭐ First Step | Complete 1 lesson |
| 🌟 Getting Started | Complete 5 lessons |
| 📚 Dedicated Learner | Complete 10 lessons |
| 🎓 Scholar | Complete 25 lessons |
| 👑 Master | Complete 50 lessons |
| 🔥 On Fire | 3-day streak |
| ⚡ Week Warrior | 7-day streak |
| 💎 Unstoppable | 14-day streak |
| 🧭 Explorer | Study 3 different topics |
| 💯 Century Club | Earn 100 XP |
| 🏆 XP Hunter | Earn 500 XP |

### Navigation Drawer

| Item | Action |
|---|---|
| 🏠 Home | Close drawer |
| ➕ New Plan | Reset to form view |
| 📊 My Progress | Scroll to plan summary |
| 📜 History | Open HistoryActivity |
| 👤 My Profile | Open ProfileActivity |
| 📈 Analytics | Open AnalyticsActivity |
| 🏆 Leaderboard | Open LeaderboardActivity |
| ℹ About | App info toast |
| 🚪 Logout | Sign out + return to login |

### Android Dependencies

| Library | Version |
|---|---|
| Firebase BoM | 33.7.0 |
| Firebase Firestore KTX | (BoM) |
| Firebase Auth KTX | (BoM) |
| Retrofit | 2.11.0 |
| Gson Converter | 2.11.0 |
| OkHttp Logging | 4.12.0 |
| Coroutines (Android) | 1.8.1 |
| Coroutines (Play Services) | 1.8.1 |
| Lifecycle ViewModel/LiveData/Runtime | 2.8.7 |
| WorkManager | 2.9.1 |
| AndroidX Core KTX | 1.15.0 |
| AppCompat | 1.7.0 |
| Material Components | 1.12.0 |
| Activity KTX | 1.9.3 |

### Running the App

1. Place `google-services.json` in `android/app/`
2. Open `android/` in Android Studio → Sync Gradle
3. Update `BASE_URL` in `build.gradle.kts` if running backend locally
4. Run on device/emulator (min API 26)

> Default `BASE_URL` is `https://e5k7-cybersync.hf.space/` (hosted). For local dev, change to `http://<YOUR_IP>:8000/`.

---

## Firebase Setup

1. Create a project at [console.firebase.google.com](https://console.firebase.google.com/)
2. Enable **Cloud Firestore** (test mode for dev)
3. Enable **Authentication** → Sign-in method → Enable **Email/Password** + **Anonymous**
4. **Backend:** Project Settings → Service accounts → Generate new private key → save as `backend/serviceAccountKey.json`
5. **Android:** Project Settings → Your apps → Android (package: `com.lonelytrack`) → Download `google-services.json`

**Firestore Security Rules:**
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /learning_plans/{planId} {
      allow read: if true;
      allow write: if false;
    }
    match /user_points/{userId} {
      allow read: if true;
      allow write: if false;
    }
    match /reminder_settings/{userId} {
      allow read: if true;
      allow write: if false;
    }
  }
}
```

**Firestore Collections:**

| Collection | Key Fields |
|---|---|
| `learning_plans` | `user_id`, `topic`, `goal`, `total_days`, `schedule[]`, `created_at`, `simplified_at`, `difficulty_increased_at` |
| `user_points` | `total_points`, `current_streak`, `best_streak`, `last_completed_date` |
| `reminder_settings` | `user_id`, `enabled`, `hour`, `minute` |

---

## License

Built at the **AI Agent Hackathon by The Product Space**. MIT License.
