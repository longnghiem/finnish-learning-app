package me.longng.finnish_learning_mobile.data.api.dto

import me.longng.finnish_learning_mobile.data.model.Card
import me.longng.finnish_learning_mobile.data.model.Dashboard
import me.longng.finnish_learning_mobile.data.model.EvaluateSentence
import me.longng.finnish_learning_mobile.data.model.QuizCard
import me.longng.finnish_learning_mobile.data.model.SubmitAnswer
import me.longng.finnish_learning_mobile.data.model.Topic
import me.longng.finnish_learning_mobile.data.model.TopicProgress
import me.longng.finnish_learning_mobile.util.absoluteImageUrl

internal fun TopicResponse.toDomain() = Topic(
    id = id,
    name = name,
    totalCards = totalCards,
)

internal fun CardResponse.toDomain() = Card(
    id = id,
    topicId = topicId,
    name = name,
    exampleSentence = exampleSentence,
    translation = translation,
    imageUrl = absoluteImageUrl(imageUrl),
)

internal fun QuizCardResponse.toDomain() = QuizCard(
    cardId = cardId,
    topicId = topicId,
    name = name,
    exampleSentence = exampleSentence,
    translation = translation,
    imageUrl = absoluteImageUrl(imageUrl),
    isNew = isNew,
)

internal fun SubmitAnswerResponse.toDomain() = SubmitAnswer(
    cardId = cardId,
    correct = correct,
)

internal fun TopicProgressResponse.toDomain() = TopicProgress(
    topicId = topicId,
    topicName = topicName,
    totalCards = totalCards,
    learnedCards = learnedCards,
    dueCards = dueCards,
    accuracy = accuracy,
    currentStreak = currentStreak,
    bestStreak = bestStreak,
)

internal fun DashboardResponse.toDomain() = Dashboard(
    totalReviews = totalReviews,
    correctReviews = correctReviews,
    overallAccuracy = overallAccuracy,
    currentStreak = currentStreak,
    bestStreak = bestStreak,
    totalDueCards = totalDueCards,
    topicProgress = topicProgress.map { it.toDomain() },
)

internal fun EvaluateSentenceResponse.toDomain() = EvaluateSentence(
    hasTypo = hasTypo,
    hasGrammarMistake = hasGrammarMistake,
    wordUsedCorrectly = wordUsedCorrectly,
    cefrLevel = FinnishLevel.valueOf(cefrLevel.name),
    feedback = feedback,
    correction = correction,
)