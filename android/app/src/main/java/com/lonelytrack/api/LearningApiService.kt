package com.lonelytrack.api

import com.lonelytrack.model.GeneratePlanResponse
import com.lonelytrack.model.HistoryResponse
import com.lonelytrack.model.PlanDetailResponse
import com.lonelytrack.model.StatusUpdate
import com.lonelytrack.model.TutorialRequest
import com.lonelytrack.model.TutorialResponse
import com.lonelytrack.model.UpdateStatusResponse
import com.lonelytrack.model.UserRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

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
}
