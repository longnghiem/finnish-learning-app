package me.longng.finnish_learning_backend.domain

import java.time.Instant
import java.time.LocalDate

data class ReviewSchedule(
    val id: Int,
    val userId: Int,
    val cardId: Int,
    val repetition: Int,
    val easeFactor: Double,
    val intervalDays: Int,
    val nextReviewDate: LocalDate,
    val lastReviewedAt: Instant?,
)