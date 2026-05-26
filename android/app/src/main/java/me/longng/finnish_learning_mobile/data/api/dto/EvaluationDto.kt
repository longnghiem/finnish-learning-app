package me.longng.finnish_learning_mobile.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request body for `POST /api/evaluate-sentence`.
 */
@JsonClass(generateAdapter = true)
data class EvaluateSentenceRequest(
    val sentence: String,
    val word: String,
    val meaning: String,
)

@JsonClass(generateAdapter = true)
data class EvaluateSentenceResponse(
    val hasTypo: Boolean,
    val hasGrammarMistake: Boolean,
    val wordUsedCorrectly: Boolean,
    val cefrLevel: FinnishLevel,
    val feedback: String,
    val correction: String?,
)

/**
 * CEFR sub-levels reported by the AI evaluator.
 */
enum class FinnishLevel {
    @Json(name = "A1.1") A1_1,
    @Json(name = "A1.2") A1_2,
    @Json(name = "A2.1") A2_1,
    @Json(name = "A2.2") A2_2,
    @Json(name = "B1.1") B1_1,
    @Json(name = "B1.2") B1_2,
}