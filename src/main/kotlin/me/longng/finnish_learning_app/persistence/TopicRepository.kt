package me.longng.finnish_learning_app.persistence

import me.longng.finnish_learning_app.domain.Topic

/**
 * Read-only access to the [Topic] reference data.
 */
interface TopicRepository {
    /** Returns all topics ordered by name. */
    fun findAll(): List<Topic>

    /** Returns a single topic by its primary key, or null if not found. */
    fun findById(id: Int): Topic?
}