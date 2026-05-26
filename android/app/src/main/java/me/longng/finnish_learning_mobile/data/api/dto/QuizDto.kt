package me.longng.finnish_learning_mobile.data.api.dto

import com.squareup.moshi.JsonClass
import java.time.LocalDate

@JsonClass(generateAdapter = true)
data class QuizCardResponse(
    val cardId: Int,
    val topicId: Int,
    val name: String,
    val exampleSentence: String,
    val translation: String,
    val imageUrl: String,
    val isNew: Boolean,
    val repetition: Int?,
    val nextReviewDate: LocalDate?,
)

/**
 * Request body for `POST /api/quiz/answer`.
 */
@JsonClass(generateAdapter = true)
data class SubmitAnswerRequest(
    val cardId: Int,
    val quality: Int,
)

/**
 * Returned after submitting a quiz answer
 */
@JsonClass(generateAdapter = true)
data class SubmitAnswerResponse(
    val cardId: Int,
    val repetition: Int,
    val easeFactor: Double,
    val intervalDays: Int,
    val nextReviewDate: LocalDate,
    val correct: Boolean,
)