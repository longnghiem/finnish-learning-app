package me.longng.finnish_learning_backend.controller.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Evaluation result returned to the client.
 *
 * @property hasTypo            `true` when the sentence contains at least one typo.
 * @property hasGrammarMistake  `true` when the sentence has a grammatical mistake.
 * @property wordUsedCorrectly  `true` when the target word is used in the correct context and inflected properly.
 * @property cefrLevel          CEFR sub-level the AI assigns to the sentence.
 * @property feedback           2–5 sentences of English feedback for the learner.
 * @property correction         Corrected Finnish sentence; non-null only when needed.
 */
data class EvaluateSentenceResponse(
    val hasTypo: Boolean,
    val hasGrammarMistake: Boolean,
    val wordUsedCorrectly: Boolean,
    val cefrLevel: FinnishLevel,
    val feedback: String,
    val correction: String?,
)

/**
 * CEFR sub-levels reported by the AI evaluator. JSON values carry a dot
 * (e.g. `"A1.1"`) which is illegal in Kotlin identifiers, so the enum
 * constants use underscores and map via [JsonProperty].
 *
 * Declaration order is significant: ordinal comparisons are used to
 * decide whether a sentence is below B1. Do not reorder.
 */
enum class FinnishLevel {
    @JsonProperty("A1.1") A1_1,
    @JsonProperty("A1.2") A1_2,
    @JsonProperty("A2.1") A2_1,
    @JsonProperty("A2.2") A2_2,
    @JsonProperty("B1.1") B1_1,
    @JsonProperty("B1.2") B1_2,
}
