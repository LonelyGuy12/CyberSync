package com.lonelytrack.api

import com.lonelytrack.model.AnalyticsResponse
import com.lonelytrack.model.DeletePlanResponse
import com.lonelytrack.model.GeneratePlanResponse
import com.lonelytrack.model.HistoryResponse
import com.lonelytrack.model.LeaderboardResponse
import com.lonelytrack.model.PlanDetailResponse
import com.lonelytrack.model.PointsResponse
import com.lonelytrack.model.ProfileResponse
import com.lonelytrack.model.QuizRequest
import com.lonelytrack.model.QuizResponse
import com.lonelytrack.model.QuizResult
import com.lonelytrack.model.QuizSubmission
import com.lonelytrack.model.ReminderSettings
import com.lonelytrack.model.ReminderSettingsResponse
import com.lonelytrack.model.StatusUpdate
import com.lonelytrack.model.TutorialRequest
import com.lonelytrack.model.TutorialResponse
import com.lonelytrack.model.UpdateStatusResponse
import com.lonelytrack.model.UserRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface LearningApiService {

    @POST("generate-plan")
    suspend fun generatePlan(@Body request: UserRequest): GeneratePlanResponse

    @POST("update-status")
    suspend fun updateStatus(@Body request: StatusUpdate): UpdateStatusResponse

    @GET("plan/{planId}")
    suspend fun getPlan(@Path("planId") planId: String): PlanDetailResponse

    @POST("generate-tutorial")
    suspend fun generateTutorial(@Body request: TutorialRequest): TutorialResponse

    @GET("history/{userId}")
    suspend fun getHistory(@Path("userId") userId: String): HistoryResponse

    @DELETE("plan/{planId}")
    suspend fun deletePlan(
        @Path("planId") planId: String,
        @Query("user_id") userId: String
    ): DeletePlanResponse

    @GET("points/{userId}")
    suspend fun getPoints(@Path("userId") userId: String): PointsResponse

    @GET("profile/{userId}")
    suspend fun getProfile(@Path("userId") userId: String): ProfileResponse

    @POST("generate-quiz")
    suspend fun generateQuiz(@Body request: QuizRequest): QuizResponse

    @POST("submit-quiz")
    suspend fun submitQuiz(@Body submission: QuizSubmission): QuizResult

    @GET("leaderboard")
    suspend fun getLeaderboard(): LeaderboardResponse

    @GET("analytics/{userId}")
    suspend fun getAnalytics(@Path("userId") userId: String): AnalyticsResponse

    @POST("reminder-settings")
    suspend fun saveReminderSettings(@Body settings: ReminderSettings): Map<String, String>

    @GET("reminder-settings/{userId}")
    suspend fun getReminderSettings(@Path("userId") userId: String): ReminderSettingsResponse
}
