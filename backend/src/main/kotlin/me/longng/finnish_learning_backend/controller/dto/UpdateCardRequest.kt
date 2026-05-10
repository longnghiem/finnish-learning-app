package me.longng.finnish_learning_backend.controller.dto

/**
 * Request payload for updating an existing card.
 * Image replacement is handled separately via a multipart file part in the controller.
 */
data class UpdateCardRequest(
    val name: String?,
    val exampleSentence: String?,
    val translation: String?,
    val topicId: Int?,
)