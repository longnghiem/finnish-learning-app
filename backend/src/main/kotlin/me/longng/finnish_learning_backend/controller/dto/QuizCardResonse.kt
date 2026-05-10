package me.longng.finnish_learning_backend.controller.dto

import java.time.LocalDate

/**
 * API response representing a single card in a quiz session.
 * - [isNew]: frontend can show a "New card!" badge
 * - [repetition]: indicates how well the user knows the card (higher = better known)
 * - [nextReviewDate]: explains why the card appeared ("due today" vs. "new card")
 */
data class QuizCardResponse(
    val cardId: Int,
    val topicId: Int,
    val name: String,
    val exampleSentence: String,
    val translation: String,
    val imageUrl: String,
    val isNew: Boolean,
    val repetition: Int?,
    val nextReviewDate: LocalDate?,
)