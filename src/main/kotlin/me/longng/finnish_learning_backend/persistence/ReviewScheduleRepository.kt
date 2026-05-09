package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.domain.ReviewSchedule
import java.time.LocalDate

/**
 * Each row represents one user's SM-2 spaced repetition state for a single card.
 */
interface ReviewScheduleRepository {
    /**
     * Inserts a new review schedule or updates the existing one for the given
     * user–card pair. Uses PostgreSQL `INSERT … ON CONFLICT … DO UPDATE` so both
     * first-review and subsequent-review cases are handled atomically.
     */
    fun upsert(
        userId: Int,
        cardId: Int,
        repetition: Int,
        easeFactor: Double,
        intervalDays: Int,
        nextReviewDate: LocalDate,
    ): ReviewSchedule

    /** Returns the schedule for a specific user–card pair, or null if the user has never reviewed this card. */
    fun findByUserAndCard(userId: Int, cardId: Int): ReviewSchedule?

    /**
     * Returns review schedules for cards that are due (next_review_date ≤ today)
     * within a given topic, ordered by next_review_date ASC (most overdue first).
     */
    fun findDueCards(userId: Int, topicId: Int, limit: Int): List<ReviewSchedule>

    /** Counts how many cards in a topic the user has reviewed at least once. */
    fun countByUserAndTopic(userId: Int, topicId: Int): Int

    /** Counts how many cards in a topic are due for review today. */
    fun countDueByUserAndTopic(userId: Int, topicId: Int): Int
}