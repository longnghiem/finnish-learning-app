package me.longng.finnish_learning_mobile.data.api.service

import me.longng.finnish_learning_mobile.data.api.dto.QuizCardResponse
import me.longng.finnish_learning_mobile.data.api.dto.SubmitAnswerRequest
import me.longng.finnish_learning_mobile.data.api.dto.SubmitAnswerResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface QuizApi {

    @GET("api/quiz/topics/{topicId}/cards")
    suspend fun nextBatch(
        @Path("topicId") topicId: Int,
        @Query("limit") limit: Int = 10,
    ): List<QuizCardResponse>

    @POST("api/quiz/answer")
    suspend fun submit(@Body body: SubmitAnswerRequest): SubmitAnswerResponse
}