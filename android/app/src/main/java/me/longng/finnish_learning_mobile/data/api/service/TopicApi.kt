package me.longng.finnish_learning_mobile.data.api.service

import me.longng.finnish_learning_mobile.data.api.dto.TopicResponse
import retrofit2.http.GET

interface TopicApi {

    @GET("api/topics")
    suspend fun list(): List<TopicResponse>
}