package com.lonelytrack.model

import com.google.gson.annotations.SerializedName

// ── Request models (match Python Pydantic schemas) ──────────────────────────

data class UserRequest(
    @SerializedName("user_id") val userId: String,
    val topic: String,
    @SerializedName("daily_minutes") val dailyMinutes: Int,
    @SerializedName("total_days") val totalDays: Int = 14,
    @SerializedName("skill_level") val skillLevel: String   // beginner | intermediate | advanced
)

data class StatusUpdate(
    @SerializedName("user_id") val userId: String,
    @SerializedName("plan_id") val planId: String,
    val day: Int,
    val status: String   // completed | missed
)

// ── Response models ─────────────────────────────────────────────────────────

data class DailyTask(
    val day: Int,
    val topic: String,
    @SerializedName("duration_mins") val durationMins: Int,
    val status: String   // pending | completed | missed
)

data class LearningPlan(
    val goal: String,
    @SerializedName("total_days") val totalDays: Int,
    val schedule: List<DailyTask>
)

data class GeneratePlanResponse(
    @SerializedName("plan_id") val planId: String,
    val goal: String,
    @SerializedName("total_days") val totalDays: Int,
    val schedule: List<DailyTask>
)

data class UpdateStatusResponse(
    val message: String
)

data class PlanDetailResponse(
    @SerializedName("plan_id") val planId: String,
    @SerializedName("user_id") val userId: String,
    val goal: String,
    @SerializedName("total_days") val totalDays: Int,
    val schedule: List<DailyTask>
)

// ── History models ──────────────────────────────────────────────────────────

data class HistoryResponse(
    val plans: List<HistoryPlanSummary>
)

data class HistoryPlanSummary(
    @SerializedName("plan_id") val planId: String,
    val goal: String,
    val topic: String,
    @SerializedName("total_days") val totalDays: Int,
    @SerializedName("completed_days") val completedDays: Int,
    @SerializedName("created_at") val createdAt: String
)

// ── Tutorial models ─────────────────────────────────────────────────────────

data class TutorialRequest(
    val topic: String,
    @SerializedName("skill_level") val skillLevel: String
)

data class TutorialResponse(
    val topic: String,
    val tutorial: String
)

data class DeletePlanResponse(
    val message: String
)
