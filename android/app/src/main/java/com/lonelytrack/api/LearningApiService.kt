package com.lonelytrack.api

import com.lonelytrack.model.DeletePlanResponse
import com.lonelytrack.model.GeneratePlanResponse
import com.lonelytrack.model.HistoryResponse
import com.lonelytrack.model.PlanDetailResponse
import com.lonelytrack.model.PointsResponse
import com.lonelytrack.model.ProfileResponse
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
}
