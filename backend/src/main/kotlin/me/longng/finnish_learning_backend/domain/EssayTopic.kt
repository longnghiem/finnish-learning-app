package me.longng.finnish_learning_backend.domain

/**
 * One predefined essay prompt shown in the essay page dropdown.
 */
data class EssayTopic (
    val id: Int,
    val topicId: Int,
    val title: String,
)