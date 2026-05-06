package me.longng.finnish_learning_backend.controller.dto

/**
 * Topic progress for a user.
 *
 * Combines two data sources:
 *   * Synchronous (`cards`, `review_schedules`) → totalCards, learnedCards, dueCards
 *   * Asynchronous (`user_topic_stats`, populated by the Kafka consumer) → accuracy, currentStreak, bestStreak
 *
 * @property topicId         Primary key of the topic.
 * @property topicName       Display name of the topic ("Arkielämä", …).
 * @property totalCards      Total number of cards in the topic (across all users).
 * @property learnedCards    Cards the user has reviewed at least once.
 * @property dueCards        Cards whose next_review_date is today or earlier.
 * @property accuracy        correctReviews / totalReviews in [0.0, 1.0]; 0.0 when no reviews yet.
 * @property currentStreak   Consecutive correct answers (resets on a wrong answer).
 * @property bestStreak      All-time longest streak for this user–topic pair.
 */
data class TopicProgressResponse(
    val topicId: Int,
    val topicName: String,
    val totalCards: Int,
    val learnedCards: Int,
    val dueCards: Int,
    val accuracy: Double,
    val currentStreak: Int,
    val bestStreak: Int,
)