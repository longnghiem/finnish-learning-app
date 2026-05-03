package me.longng.finnish_learning_backend.controller.dto

import java.time.LocalDate

/**
 * Response returned after submitting a quiz answer.
 *
 * Contains the updated SM-2 schedule state so the frontend can show
 * immediate feedback: "Next review in X days" or "You'll see this card
 * again tomorrow."
 *
 * @property cardId The card that was reviewed.
 * @property repetition Updated consecutive-correct count (0 if answer was incorrect).
 * @property easeFactor Updated ease factor (≥ 1.3).
 * @property intervalDays Days until the next review.
 * @property nextReviewDate The calendar date of the next scheduled review.
 * @property correct Whether the answer was considered correct (quality ≥ 3).
 */
data class SubmitAnswerResponse(
    val cardId: Int,
    val repetition: Int,
    val easeFactor: Double,
    val intervalDays: Int,
    val nextReviewDate: LocalDate,
    val correct: Boolean,
)