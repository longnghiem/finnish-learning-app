package me.longng.finnish_learning_mobile.data.repositories

import me.longng.finnish_learning_mobile.data.api.dto.SubmitAnswerRequest
import me.longng.finnish_learning_mobile.data.api.dto.toDomain
import me.longng.finnish_learning_mobile.data.api.service.QuizApi
import me.longng.finnish_learning_mobile.data.model.QuizCard
import me.longng.finnish_learning_mobile.data.model.SubmitAnswer
import me.longng.finnish_learning_mobile.util.AppResult
import me.longng.finnish_learning_mobile.util.runCatchingApp

class QuizRepository(private val api: QuizApi) {
    suspend fun fetchQuizCards(
        topicId: Int,
        limit: Int = 10,
    ): AppResult<List<QuizCard>> = runCatchingApp {
        api.fetchQuizCards(topicId, limit).map { it.toDomain() }
    }

    suspend fun submitAnswer(
        cardId: Int,
        quality: Int,
    ): AppResult<SubmitAnswer> = runCatchingApp {
        require(quality in setOf(1, 3, 4, 5)) { "quality must be one of 1, 3, 4, 5" }
        api.submitAnswer(SubmitAnswerRequest(
            cardId = cardId,
            quality =  quality,
        )).toDomain()
    }
}