package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.domain.EssayTopic

interface EssayTopicRepository {

    /** Returns all essay prompts for one topic */
    fun findByTopicId(topicId: Int): List<EssayTopic>

    /** Returns a single essay prompt by its primary key, or null if not found. */
    fun findById(id: Int): EssayTopic?
}