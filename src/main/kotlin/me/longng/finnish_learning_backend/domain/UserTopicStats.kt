package me.longng.finnish_learning_backend.domain

import java.time.Instant

data class UserTopicStats(
    val id: Int,
    val userId: Int,
    val topicId: Int,
    val totalReviews: Int,
    val correctReviews: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val lastReviewedAt: Instant?,
    val updatedAt: Instant,
)