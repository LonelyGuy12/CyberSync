---
title: CyberSync - LonelyTrack Backend
emoji: 🧠
colorFrom: purple
colorTo: blue
sdk: docker
app_port: 7860
pinned: false
---

# LonelyTrack – AI-Powered Learning Consistency Agent

Backend API for the LonelyTrack adaptive study planner.

## Endpoints

- `POST /generate-plan` — Generate an AI-powered study schedule
- `POST /update-status` — Mark a day as completed/missed
- `POST /generate-tutorial` — Get an AI tutorial for a topic
- `GET /plan/{plan_id}` — Fetch a specific plan
- `GET /history/{user_id}` — Get all plans for a user
- `DELETE /plan/{plan_id}` — Delete a plan

## Setup

This Space requires the following **Secrets** (Settings → Secrets):

| Secret | Description |
|---|---|
| `OPENROUTER_API_KEY` | Your OpenRouter API key |
| `FIREBASE_SERVICE_ACCOUNT_KEY_JSON` | Full JSON content of your Firebase service account key |
