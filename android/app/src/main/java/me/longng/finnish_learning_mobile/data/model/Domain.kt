package me.longng.finnish_learning_mobile.data.model

import me.longng.finnish_learning_mobile.data.api.dto.FinnishLevel
import java.time.LocalDate

data class Topic (
    val id: Int,
    val name: String,
    val totalCards: Int,
)

data class Card (
    val id: Int,
    val topicId: Int,
    val name: String,
    val exampleSentence: String,
    val translation: String,
    val imageUrl: String,
)

data class QuizCard (
    val cardId: Int,
    val topicId: Int,
    val name: String,
    val exampleSentence: String,
    val translation: String,
    val imageUrl: String,
    val isNew: Boolean,
)

data class SubmitAnswer (
    val cardId: Int,
    val correct: Boolean,
)

data class TopicProgress (
    val topicId: Int,
    val topicName: String,
    val totalCards: Int,
    val learnedCards: Int,
    val dueCards: Int,
    val accuracy: Double,
    val currentStreak: Int,
    val bestStreak: Int,
)

data class Dashboard(
    val totalReviews: Int,
    val correctReviews: Int,
    val overallAccuracy: Double,
    val currentStreak: Int,
    val bestStreak: Int,
    val totalDueCards: Int,
    val topicProgress: List<TopicProgress>,
)

enum class FinnishLevel { A1_1, A1_2, A2_1, A2_2, B1_1, B1_2 }

data class EvaluateSentence(
    val hasTypo: Boolean,
    val hasGrammarMistake: Boolean,
    val wordUsedCorrectly: Boolean,
    val cefrLevel: FinnishLevel,
    val feedback: String,
    val correction: String?,
)