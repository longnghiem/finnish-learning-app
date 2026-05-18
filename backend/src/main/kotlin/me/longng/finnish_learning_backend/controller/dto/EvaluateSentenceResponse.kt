package me.longng.finnish_learning_backend.controller.dto

/**
 * Evaluation result returned to the client.
 *
 * Invariants enforced by `SentenceEvaluationService` *after* parsing the raw
 * AI response — clients may rely on them:
 *
 * @property hasGrammarMistake  `true` when the sentence has grammatical mistake.
 * @property hasTypo            `true` when the sentence contains at least one typo.
 * @property level              CEFR level the AI assigns to the sentence.
 * @property correction         Corrected version of the sentence; non-null only when needed.
 * @property b1Example          Suggested B1-level sentence using the user's content word;
 *                              non-null only when [level] is below B1.
 */
data class EvaluateSentenceResponse(
    val hasGrammarMistake: Boolean,
    val hasTypo: Boolean,
    val level: FinnishLevel,
    val correction: String?,
    val b1Example: String?,
)

/**
 * CEFR proficiency levels reported by the AI evaluator.
 *
 * Declaration order is significant: any value whose ordinal is strictly less
 * than [B1].ordinal triggers the "show a B1 example" branch in the service.
 * Do not reorder.
 */
enum class FinnishLevel { A1, A2, B1, B2, C1, C2 }