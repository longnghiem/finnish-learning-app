package me.longng.finnish_learning_mobile.data.api.service

import me.longng.finnish_learning_mobile.data.api.dto.DashboardResponse
import me.longng.finnish_learning_mobile.data.api.dto.TopicProgressResponse
import retrofit2.http.GET

interface ProgressApi {

    @GET("api/progress/topics")
    suspend fun topics(): List<TopicProgressResponse>

    @GET("api/progress/dashboard")
    suspend fun dashboard(): DashboardResponse
}