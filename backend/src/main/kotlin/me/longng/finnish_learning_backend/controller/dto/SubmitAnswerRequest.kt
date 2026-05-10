package me.longng.finnish_learning_backend.controller.dto

/**
 * Request body for submitting a quiz answer.
 * @property quality Self-assessed recall quality (1, 3, 4, or 5 in practice).
 *   Maps to: Again=1, Hard=3, Good=4, Easy=5.
 */
data class SubmitAnswerRequest(
    val cardId: Int,
    val quality: Int,
)