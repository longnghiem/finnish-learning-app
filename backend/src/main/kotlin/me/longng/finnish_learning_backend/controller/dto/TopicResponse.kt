package me.longng.finnish_learning_backend.controller.dto

import me.longng.finnish_learning_backend.domain.Topic

/**
 * API response payload representing a single topic.
 */
data class TopicResponse(
    val id: Int,
    val name: String,
    val totalCards: Int,
) {
    companion object {
        fun from(topic: Topic, totalCards: Int): TopicResponse = TopicResponse(
            id = topic.id,
            name = topic.name,
            totalCards = totalCards,
        )
    }
}