package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.domain.UserTopicStats

/**
 * This table is the write target of the Kafka consumer ([QuizStatsConsumer]).
 * It stores pre-aggregated per-user-per-topic statistics so the progress
 * dashboard can read them without expensive real-time aggregation.
 */
interface UserTopicStatsRepository {

    /**
     * Atomically increments review counters and updates the streak for a
     * user–topic pair. Called by the Kafka consumer on each [QuizAnswerEvent].
     *
     * Uses `INSERT … ON CONFLICT (user_id, topic_id) DO UPDATE` so that the
     * first answer for a topic creates the row and subsequent answers update it.
     *
     * @param correct Whether the quiz answer was correct (quality ≥ 3).
     */
    fun upsert(userId: Int, topicId: Int, correct: Boolean)

    /** Returns all topic stats for a user (one row per topic the user has studied). */
    fun findByUser(userId: Int): List<UserTopicStats>

    /** Returns stats for a specific user–topic pair, or null if never studied. */
    fun findByUserAndTopic(userId: Int, topicId: Int): UserTopicStats?
}