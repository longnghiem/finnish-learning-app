package me.longng.finnish_learning_backend.controller.dto

/**
 * Aggregate dashboard for a user.
 *
 * The aggregate fields ([totalReviews], [correctReviews], [overallAccuracy],
 * [currentStreak], [bestStreak], [totalDueCards]) are derived from
 * [topicProgress]. They are computed on the server-side.
 *
 * @property totalReviews      Sum of `total_reviews` across all topics studied.
 * @property correctReviews    Sum of `correct_reviews` across all topics.
 * @property overallAccuracy   correctReviews / totalReviews in [0.0, 1.0]; 0.0 when no reviews.
 * @property currentStreak     Max per-topic current streak.
 * @property bestStreak        Max per-topic best streak.
 * @property totalDueCards     Sum of per-topic dueCards.
 * @property topicProgress     Per-topic breakdown (one entry per topic, even if untouched).
 */
data class DashboardResponse(
    val totalReviews: Int,
    val correctReviews: Int,
    val overallAccuracy: Double,
    val currentStreak: Int,
    val bestStreak: Int,
    val totalDueCards: Int,
    val topicProgress: List<TopicProgressResponse>,
)