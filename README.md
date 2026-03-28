# CyberSync

AI-powered learning consistency agent that generates personalized study plans, tracks daily progress, auto-simplifies when you fall behind, and generates on-demand tutorials — all backed by an LLM and Firebase.

---

## Table of Contents

- [Architecture](#architecture)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Backend (FastAPI)](#backend-fastapi)
  - [Endpoints](#endpoints)
  - [Pydantic Models](#pydantic-models)
  - [Environment Variables](#environment-variables)
  - [Dependencies](#backend-dependencies)
  - [Running the Backend](#running-the-backend)
- [Android App (Kotlin)](#android-app-kotlin)
  - [Build Configuration](#build-configuration)
  - [Project Structure](#project-structure)
  - [Activities](#activities)
  - [API Layer](#api-layer)
  - [Data Models](#data-models)
  - [ViewModel](#viewmodel)
  - [Navigation Drawer](#navigation-drawer)
  - [Dependencies](#android-dependencies)
  - [Running the App](#running-the-app)
- [Firebase Setup](#firebase-setup)
- [Gitignore](#gitignore)
- [License](#license)

---

## Architecture

```
┌─────────────────┐        HTTP/REST        ┌──────────────────┐
│  Android App    │ ◄─────────────────────► │  FastAPI Backend  │
│  (Kotlin/MVVM)  │                          │  (Python)         │
└────────┬────────┘                          └────────┬──────────┘
         │                                            │
         │ Firestore SDK                              │ Firebase Admin SDK
         │ (real-time listener)                       │ (read/write)
         ▼                                            ▼
       ┌──────────────────────────────────────────────────┐
       │              Firebase Firestore                   │
       │           Collection: learning_plans              │
       └──────────────────────────────────────────────────┘
                                                      │
                                              LLM via OpenRouter
                                                      │
                                              ┌───────▼───────┐
                                              │  Gemini 2.0   │
                                              │  Flash / Any   │
                                              │  OpenRouter    │
                                              │  Model         │
                                              └────────────────┘
```

---

## Features

| Feature | Description |
|---|---|
| **AI Plan Generation** | LLM creates a day-by-day structured study schedule tailored to your topic, time budget, skill level, and duration |
| **Daily Task Tracking** | Mark each day as completed or missed; progress shown in-app |
| **Auto-Simplification** | Backend detects 3 consecutive missed days and triggers LLM to re-generate a simplified remaining schedule (~25% lighter) |
| **On-Demand Tutorials** | Tap any day's topic to get a full AI-generated tutorial (800–1200 words) |
| **Real-Time Sync** | Android app listens to Firestore snapshots — schedule changes (including simplification) appear instantly |
| **Plan History** | View all past learning plans with completion stats |
| **Navigation Drawer** | Hamburger menu with Home, New Plan, My Progress, History, and About |
| **Slide Animations** | Smooth slide-in/slide-out activity transitions |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Python 3, FastAPI, Uvicorn |
| LLM | OpenRouter API (default: `google/gemini-2.0-flash-001`) |
| Database | Firebase Firestore |
| Android | Kotlin, MVVM, ViewBinding, Retrofit 2, OkHttp, Coroutines, LiveData |
| Build | Gradle 8.7.3 (Kotlin DSL), AGP 8.7.3, Kotlin 2.0.21 |

---

## Backend (FastAPI)

### Endpoints

#### `POST /generate-plan`

Generate an AI-powered learning plan.

**Request body:**
```json
{
  "user_id": "string",
  "topic": "string",
  "daily_minutes": 30,
  "total_days": 14,
  "skill_level": "beginner"
}
```

| Field | Type | Constraints |
|---|---|---|
| `user_id` | `string` | required |
| `topic` | `string` | required |
| `daily_minutes` | `int` | 1–480 |
| `total_days` | `int` | 1–365, default 14 |
| `skill_level` | `string` | `beginner` \| `intermediate` \| `advanced` |

**Response:**
```json
{
  "plan_id": "firestore-doc-id",
  "goal": "One-sentence learning goal",
  "total_days": 14,
  "schedule": [
    { "day": 1, "topic": "Sub-topic name", "duration_mins": 30, "status": "pending" }
  ]
}
```

---

#### `POST /update-status`

Mark a specific day as completed or missed. Triggers background simplification check if 3 consecutive days are missed.

**Request body:**
```json
{
  "user_id": "string",
  "plan_id": "string",
  "day": 1,
  "status": "completed"
}
```

| Field | Type | Constraints |
|---|---|---|
| `user_id` | `string` | required |
| `plan_id` | `string` | required |
| `day` | `int` | required |
| `status` | `string` | `completed` \| `missed` |

**Response:**
```json
{ "message": "Day 1 marked as completed" }
```

**Errors:** `404` Plan not found · `403` Not your plan · `400` Day not found or already updated

---

#### `GET /plan/{plan_id}`

Retrieve a single learning plan by ID.

**Response:**
```json
{
  "plan_id": "string",
  "user_id": "string",
  "goal": "string",
  "total_days": 14,
  "schedule": [ ... ]
}
```

---

#### `POST /generate-tutorial`

Generate an AI tutorial for a given topic.

**Request body:**
```json
{
  "topic": "string",
  "skill_level": "beginner"
}
```

**Response:**
```json
{
  "topic": "string",
  "tutorial": "Full tutorial text (800-1200 words)"
}
```

---

#### `GET /history/{user_id}`

Get all learning plans for a user, sorted newest first.

**Response:**
```json
{
  "plans": [
    {
      "plan_id": "string",
      "goal": "string",
      "topic": "string",
      "total_days": 14,
      "completed_days": 5,
      "created_at": "ISO datetime"
    }
  ]
}
```

---

### Pydantic Models

| Model | Fields |
|---|---|
| `UserRequest` | `user_id`, `topic`, `daily_minutes` (1–480), `total_days` (1–365), `skill_level` |
| `DailyTask` | `day`, `topic`, `duration_mins`, `status` (pending/completed/missed) |
| `LearningPlan` | `goal`, `total_days`, `schedule: list[DailyTask]` |
| `StatusUpdate` | `user_id`, `plan_id`, `day`, `status` (completed/missed) |
| `TutorialRequest` | `topic`, `skill_level` |

### Background Logic: Auto-Simplification

When a status update is received, a background task (`check_and_simplify`) runs:
1. Reads the plan's schedule from Firestore
2. Detects 3+ consecutive missed days
3. Sends remaining tasks to the LLM with instructions to reduce complexity ~25%
4. Replaces pending/missed tasks in Firestore (keeps completed tasks)
5. Adds a `simplified_at` timestamp

### Environment Variables

Create `backend/.env` from the example:

```bash
cp backend/.env.example backend/.env
```

| Variable | Required | Default | Description |
|---|---|---|---|
| `FIREBASE_SERVICE_ACCOUNT_KEY_PATH` | Yes | `serviceAccountKey.json` | Path to Firebase service account JSON key |
| `OPENROUTER_API_KEY` | Yes | — | API key from [OpenRouter](https://openrouter.ai/keys) |
| `OPENROUTER_MODEL` | No | `google/gemini-2.0-flash-001` | Any OpenRouter-supported model ID |

### Backend Dependencies

| Package | Version |
|---|---|
| `fastapi` | 0.115.0 |
| `uvicorn[standard]` | 0.30.6 |
| `pydantic` | 2.9.2 |
| `firebase-admin` | 6.5.0 |
| `openai` | ≥1.51.0 |
| `python-dotenv` | 1.0.1 |

### Running the Backend

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# Place your Firebase service account key as serviceAccountKey.json
# Configure .env (see above)

uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

The API will be available at `http://localhost:8000`. Interactive docs at `http://localhost:8000/docs`.

---

## Android App (Kotlin)

**Package name:** `com.lonelytrack`  
**App label:** LonelyTrack  
**Min SDK:** 26 (Android 8.0)  
**Target / Compile SDK:** 35  
**Version:** 0.1.0 (versionCode 1)  
**Java target:** 17

### Build Configuration

| Setting | Value |
|---|---|
| AGP | 8.7.3 |
| Kotlin | 2.0.21 |
| Google Services plugin | 4.4.2 |
| `compileSdk` | 35 |
| `minSdk` | 26 |
| `targetSdk` | 35 |
| `BASE_URL` | `http://10.152.159.116:8000/` (BuildConfig field — update for your network) |
| ViewBinding | enabled |
| BuildConfig | enabled |

### Project Structure

```
android/app/src/main/java/com/lonelytrack/
├── MainActivity.kt            # Main screen: plan form, schedule list, progress
├── TutorialActivity.kt        # AI-generated tutorial viewer
├── HistoryActivity.kt         # Past learning plans list
├── adapter/
│   ├── ScheduleAdapter.kt     # RecyclerView adapter for daily tasks
│   └── HistoryAdapter.kt      # RecyclerView adapter for plan history
├── api/
│   ├── LearningApiService.kt  # Retrofit interface (5 endpoints)
│   └── RetrofitClient.kt      # Singleton OkHttp + Retrofit builder
├── model/
│   └── Models.kt              # All data classes (request/response)
└── viewmodel/
    └── LearningViewModel.kt   # MVVM ViewModel with LiveData
```

**Resources:**
```
res/
├── layout/
│   ├── activity_main.xml        # DrawerLayout + form + schedule RecyclerView
│   ├── activity_tutorial.xml    # Tutorial viewer with loading/error states
│   ├── activity_history.xml     # History list with empty/error states
│   ├── item_day_task.xml        # Single schedule day row
│   ├── item_history_plan.xml    # Single history plan row
│   └── nav_header.xml           # Navigation drawer header
├── menu/
│   └── drawer_menu.xml          # Drawer items: Home, New Plan, Progress, History, About
├── anim/
│   ├── slide_in_left.xml
│   ├── slide_in_right.xml
│   ├── slide_out_left.xml
│   └── slide_out_right.xml
└── xml/
    └── network_security_config.xml  # Allows cleartext to 10.0.2.2 & 10.152.159.116
```

### Activities

| Activity | Purpose | Exported |
|---|---|---|
| `MainActivity` | Launcher. Plan generation form, schedule RecyclerView, progress tracking, navigation drawer | Yes (LAUNCHER) |
| `TutorialActivity` | Displays AI-generated tutorial for a tapped day's topic. Has loading spinner, error state, retry button | No |
| `HistoryActivity` | Lists all past plans for the user with completion stats. Tap to reload a plan | No |

### Permissions

| Permission | Reason |
|---|---|
| `INTERNET` | HTTP calls to FastAPI backend + Firestore |

### Network Security

Cleartext HTTP allowed for:
- `10.0.2.2` (Android emulator → host loopback)
- `10.152.159.116` (local network backend IP)

### API Layer

**`LearningApiService`** — Retrofit interface:

| Method | Endpoint | Request | Response |
|---|---|---|---|
| `generatePlan()` | `POST generate-plan` | `UserRequest` | `GeneratePlanResponse` |
| `updateStatus()` | `POST update-status` | `StatusUpdate` | `UpdateStatusResponse` |
| `getPlan()` | `GET plan/{planId}` | path param | `PlanDetailResponse` |
| `generateTutorial()` | `POST generate-tutorial` | `TutorialRequest` | `TutorialResponse` |
| `getHistory()` | `GET history/{userId}` | path param | `HistoryResponse` |

**`RetrofitClient`** — Singleton with:
- Base URL from `BuildConfig.BASE_URL`
- 30s connect / 60s read / 30s write timeouts
- `HttpLoggingInterceptor` (BODY in debug, NONE in release)
- Gson converter

### Data Models

| Class | Fields | Usage |
|---|---|---|
| `UserRequest` | `userId`, `topic`, `dailyMinutes`, `totalDays`, `skillLevel` | Plan generation request |
| `StatusUpdate` | `userId`, `planId`, `day`, `status` | Mark day completed/missed |
| `DailyTask` | `day`, `topic`, `durationMins`, `status` | A single day in a schedule |
| `LearningPlan` | `goal`, `totalDays`, `schedule` | Full plan structure |
| `GeneratePlanResponse` | `planId`, `goal`, `totalDays`, `schedule` | Create-plan response |
| `UpdateStatusResponse` | `message` | Status update response |
| `PlanDetailResponse` | `planId`, `userId`, `goal`, `totalDays`, `schedule` | Get-plan response |
| `HistoryPlanSummary` | `planId`, `goal`, `topic`, `totalDays`, `completedDays`, `createdAt` | History item |
| `HistoryResponse` | `plans: List<HistoryPlanSummary>` | History endpoint response |
| `TutorialRequest` | `topic`, `skillLevel` | Tutorial generation request |
| `TutorialResponse` | `topic`, `tutorial` | Tutorial content response |

All models use `@SerializedName` for `snake_case` ↔ `camelCase` mapping.

### ViewModel

`LearningViewModel` manages:
- **Plan generation** via Retrofit → updates `plan` and `schedule` LiveData
- **Status updates** with optimistic local update + double-tap prevention (`updatingDays` set)
- **Real-time Firestore listener** on the active plan document — schedule changes (including backend auto-simplification) propagate instantly
- **Error handling** surfaced through `error` LiveData
- **Loading state** via `loading` LiveData

### Navigation Drawer

| Item | Action |
|---|---|
| Home | Close drawer |
| New Plan | Show form, clear current plan |
| My Progress | Scroll to plan summary (or toast if no plan) |
| History | Launch `HistoryActivity` |
| About | Toast: "LonelyTrack — AI-powered learning consistency agent" |

### Android Dependencies

| Library | Version |
|---|---|
| Firebase BoM | 33.7.0 |
| Firebase Firestore KTX | (BoM-managed) |
| Retrofit | 2.11.0 |
| Retrofit Gson Converter | 2.11.0 |
| OkHttp Logging Interceptor | 4.12.0 |
| Kotlinx Coroutines (Android) | 1.8.1 |
| Kotlinx Coroutines (Play Services) | 1.8.1 |
| Lifecycle ViewModel KTX | 2.8.7 |
| Lifecycle LiveData KTX | 2.8.7 |
| Lifecycle Runtime KTX | 2.8.7 |
| AndroidX Core KTX | 1.15.0 |
| AppCompat | 1.7.0 |
| Material Components | 1.12.0 |
| Activity KTX | 1.9.3 |

### Running the App

1. **Update `BASE_URL`** in `android/app/build.gradle.kts` to point at your backend's IP:
   ```kotlin
   buildConfigField("String", "BASE_URL", "\"http://<YOUR_IP>:8000/\"")
   ```
2. Place `google-services.json` in `android/app/` (from Firebase Console).
3. Open the `android/` folder in Android Studio.
4. Sync Gradle, then **Run** on an emulator or device (min API 26).

> For emulator: use `10.0.2.2` as the host IP. For physical device: use your machine's LAN IP and ensure both are on the same network.

---

## Firebase Setup

1. Create a project in [Firebase Console](https://console.firebase.google.com/).
2. Enable **Cloud Firestore** (start in test mode or configure rules).
3. **Backend:** Generate a service account key (Project Settings → Service accounts → Generate new private key). Save as `backend/serviceAccountKey.json`.
4. **Android:** Download `google-services.json` (Project Settings → Your apps → Android) and place in `android/app/`.

**Firestore collection:** `learning_plans`

| Field | Type | Description |
|---|---|---|
| `user_id` | string | Anonymous user identifier |
| `topic` | string | Learning topic |
| `goal` | string | LLM-generated learning goal |
| `total_days` | number | Plan duration |
| `schedule` | array | List of `DailyTask` maps |
| `created_at` | string | ISO 8601 timestamp |
| `simplified_at` | string (optional) | Set when auto-simplification triggers |

---

## Gitignore

The project excludes:
- `.env`, `serviceAccountKey.json`, Firebase admin SDK keys
- Python artifacts (`__pycache__/`, `*.pyc`, `.venv/`)
- Android build artifacts (`.gradle/`, `build/`, `local.properties`)
- IDE files (`.idea/`, `*.iml`)
- macOS (`.DS_Store`)

---

## License

This project was built for a hackathon. No license specified.
