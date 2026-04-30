package me.longng.finnish_learning_backend.event

import java.time.Instant

/**
 * Event published to Kafka when a user answers a quiz question.
 *
 * The synchronous path (REST → review_schedules DB) is the source of truth.
 * This event triggers the asynchronous path: a Kafka consumer updates
 * pre-aggregated statistics in `user_topic_stats` for fast dashboard queries.
 *
 * Serialized as flat JSON via Spring Kafka's Jackson serializer.
 * Example payload:
 * ```json
 * {
 *   "userId": 1,
 *   "cardId": 42,
 *   "topicId": 3,
 *   "quality": 4,
 *   "correct": true,
 *   "timestamp": "2026-04-28T10:30:00Z"
 * }
 *
 */
data class QuizAnswerEvent(
    val userId: Int,
    val cardId: Int,
    val topicId: Int,
    val quality: Int,
    val correct: Boolean,
    val timestamp: Instant,
)