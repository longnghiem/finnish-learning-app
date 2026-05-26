package me.longng.finnish_learning_mobile.data.api.service

import me.longng.finnish_learning_mobile.data.api.dto.EvaluateSentenceRequest
import me.longng.finnish_learning_mobile.data.api.dto.EvaluateSentenceResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface EvaluationApi {

    @POST("api/evaluate-sentence")
    suspend fun evaluate(@Body body: EvaluateSentenceRequest): EvaluateSentenceResponse
}