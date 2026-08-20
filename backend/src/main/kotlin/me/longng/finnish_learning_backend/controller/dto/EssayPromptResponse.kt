package me.longng.finnish_learning_backend.controller.dto

/**
 * One selectable essay prompt, as returned by
 * `GET /api/essays/prompts?topicId={topicId}` and rendered in the essay page dropdown.
 *
 */
data class EssayPromptResponse(
    val id: Int,
    val title: String,
)