package me.longng.finnish_learning_app.controller.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

/**
 * Request payload for creating a new card.
 * Received as individual multipart form fields (not JSON).
 */
data class CreateCardRequest(
    @field:NotBlank(message = "Card name must not be blank")
    val name: String,

    @field:NotBlank(message = "Example sentence must not be blank")
    val exampleSentence: String,

    @field:NotBlank(message = "Translation must not be blank")
    val translation: String,

    @field:NotNull(message = "Topic ID is required")
    @field:Positive(message = "Topic ID must be a positive number")
    val topicId: Int,
)