package me.longng.finnish_learning_backend.controller.dto

/**
 * Request body for `POST /api/essays/evaluate`.
 */
data class EvaluateEssayRequest(
    val promptId: Int,
    val essay: String,
)

/**
 * Minimum accepted essay length in characters.
 */
const val MIN_ESSAY_LENGTH = 300


/**
 * Maximum accepted essay length in characters.
 *
 * 200 words ≈ 1,800 characters
 * Capping worst-case token cost on the Bedrock call.
 */
const val MAX_ESSAY_LENGTH = 2_500