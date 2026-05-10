package me.longng.finnish_learning_backend.controller.dto

import me.longng.finnish_learning_backend.domain.Card

/**
 * API response payload representing a single card.
 *
 * @property imageUrl Full URL path to retrieve the image.
 *                    Format: "/api/images/{imageFilename}"
 */
data class CardResponse(
    val id: Int,
    val topicId: Int,
    val name: String,
    val exampleSentence: String,
    val translation: String,
    val imageUrl: String,
    val createdAt: java.time.Instant,
    val updatedAt: java.time.Instant,
) {
    companion object {
        fun from(card: Card): CardResponse = CardResponse(
            id = card.id,
            topicId = card.topicId,
            name = card.name,
            exampleSentence = card.exampleSentence,
            translation = card.translation,
            imageUrl = "/api/images/${card.imageFilename}",
            createdAt = card.createdAt,
            updatedAt = card.updatedAt,
        )
    }
}