package me.longng.finnish_learning_app.controller.dto

import me.longng.finnish_learning_app.domain.Topic

/**
 * API response payload representing a single topic.
 */
data class TopicResponse(
    val id: Int,
    val name: String,
) {
    companion object {
        fun from(topic: Topic): TopicResponse = TopicResponse(
            id = topic.id,
            name = topic.name,
        )
    }
}