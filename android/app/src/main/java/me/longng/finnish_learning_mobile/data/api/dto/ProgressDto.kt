package me.longng.finnish_learning_mobile.data.api.dto

import com.squareup.moshi.JsonClass

/**
 * Per-topic progress for the current user.
 */
@JsonClass(generateAdapter = true)
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

/**
 * Aggregate dashboard.
 */
@JsonClass(generateAdapter = true)
data class DashboardResponse(
    val totalReviews: Int,
    val correctReviews: Int,
    val overallAccuracy: Double,
    val currentStreak: Int,
    val bestStreak: Int,
    val totalDueCards: Int,
    val topicProgress: List<TopicProgressResponse>,
)