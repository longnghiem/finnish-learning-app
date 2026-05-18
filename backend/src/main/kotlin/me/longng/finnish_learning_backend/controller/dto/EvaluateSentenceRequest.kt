package me.longng.finnish_learning_backend.controller.dto

/**
 * Request body for `POST /api/evaluate-sentence`.
 */
data class EvaluateSentenceRequest(
    val sentence: String,
)