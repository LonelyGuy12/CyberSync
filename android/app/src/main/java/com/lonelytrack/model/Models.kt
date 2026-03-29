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
    val message: String,
    @SerializedName("points_earned") val pointsEarned: Int = 0,
    @SerializedName("total_points") val totalPoints: Int = 0,
    val streak: Int = 0
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

// ── Points models ───────────────────────────────────────────────────────────

data class PointsResponse(
    @SerializedName("total_points") val totalPoints: Int,
    val streak: Int,
    @SerializedName("last_earned") val lastEarned: Int
)

// ── Profile / Achievement models ────────────────────────────────────────────

data class Trophy(
    val id: String,
    val name: String,
    val desc: String,
    val icon: String,
    val unlocked: Boolean
)

data class ProfileResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("total_points") val totalPoints: Int,
    val stars: Int,
    @SerializedName("current_streak") val currentStreak: Int,
    @SerializedName("best_streak") val bestStreak: Int,
    @SerializedName("total_completed") val totalCompleted: Int,
    @SerializedName("total_missed") val totalMissed: Int,
    @SerializedName("total_pending") val totalPending: Int,
    @SerializedName("total_plans") val totalPlans: Int,
    @SerializedName("topics_studied") val topicsStudied: List<String>,
    val trophies: List<Trophy>
)

// ── Quiz models ─────────────────────────────────────────────────────────────

data class QuizRequest(
    val topic: String,
    @SerializedName("skill_level") val skillLevel: String,
    @SerializedName("num_questions") val numQuestions: Int = 5
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    @SerializedName("correct_answer") val correctAnswer: Int,
    val explanation: String
)

data class QuizResponse(
    val topic: String,
    val questions: List<QuizQuestion>
)

data class QuizSubmission(
    @SerializedName("user_id") val userId: String,
    @SerializedName("plan_id") val planId: String,
    val day: Int,
    val answers: List<Int>
)

data class QuizResult(
    @SerializedName("score_percent") val scorePercent: Double,
    @SerializedName("bonus_xp") val bonusXp: Int,
    @SerializedName("total_points") val totalPoints: Int,
    val message: String
)

// ── Leaderboard models ──────────────────────────────────────────────────────

data class LeaderboardEntry(
    @SerializedName("user_id") val userId: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("total_points") val totalPoints: Int,
    val streak: Int,
    @SerializedName("best_streak") val bestStreak: Int
)

data class LeaderboardResponse(
    val leaderboard: List<LeaderboardEntry>
)

// ── Analytics models ────────────────────────────────────────────────────────

data class PlanSummary(
    val topic: String,
    @SerializedName("total_days") val totalDays: Int,
    val completed: Int,
    val missed: Int,
    val pending: Int,
    @SerializedName("completion_rate") val completionRate: Double
)

data class AnalyticsResponse(
    @SerializedName("total_tasks") val totalTasks: Int,
    @SerializedName("total_completed") val totalCompleted: Int,
    @SerializedName("total_missed") val totalMissed: Int,
    @SerializedName("total_pending") val totalPending: Int,
    @SerializedName("completion_rate") val completionRate: Double,
    @SerializedName("total_minutes_studied") val totalMinutesStudied: Int,
    @SerializedName("plan_summaries") val planSummaries: List<PlanSummary>
)

// ── Reminder models ─────────────────────────────────────────────────────────

data class ReminderSettings(
    @SerializedName("user_id") val userId: String,
    val enabled: Boolean = true,
    val hour: Int = 9,
    val minute: Int = 0
)

data class ReminderSettingsResponse(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int
)
