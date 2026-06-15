package me.longng.finnish_learning_mobile.data.repositories

import me.longng.finnish_learning_mobile.data.api.dto.EvaluateSentenceRequest
import me.longng.finnish_learning_mobile.data.api.dto.toDomain
import me.longng.finnish_learning_mobile.data.api.service.EvaluationApi
import me.longng.finnish_learning_mobile.data.model.EvaluateSentence
import me.longng.finnish_learning_mobile.util.AppResult
import me.longng.finnish_learning_mobile.util.runCatchingApp

class SentenceEvaluationRepository(private val api: EvaluationApi) {
    suspend fun evaluateSentence(
        sentence: String,
        word: String,
        meaning: String,
    ): AppResult<EvaluateSentence> = runCatchingApp {
        api.evaluateSentence(EvaluateSentenceRequest(sentence, word, meaning)).toDomain()
    }
}