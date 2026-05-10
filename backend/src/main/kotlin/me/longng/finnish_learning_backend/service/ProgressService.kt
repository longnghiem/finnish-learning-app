package me.longng.finnish_learning_backend.service

import me.longng.finnish_learning_backend.controller.dto.DashboardResponse
import me.longng.finnish_learning_backend.controller.dto.TopicProgressResponse
import me.longng.finnish_learning_backend.domain.UserTopicStats
import me.longng.finnish_learning_backend.persistence.CardRepository
import me.longng.finnish_learning_backend.persistence.ReviewScheduleRepository
import me.longng.finnish_learning_backend.persistence.TopicRepository
import me.longng.finnish_learning_backend.persistence.UserTopicStatsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Read-only aggregator for the user progress.
 *
 * Combines:
 *   * `topics` — the catalogue of all topics
 *   * `cards` — total cards per topic
 *   * `review_schedules` — what the user has learned and what is due now
 *   * `user_topic_stats` — Kafka-aggregated accuracy and streak
 */
@Service
@Transactional(readOnly = true)
class ProgressService(
    private val topicRepository: TopicRepository,
    private val cardRepository: CardRepository,
    private val reviewScheduleRepository: ReviewScheduleRepository,
    private val userTopicStatsRepository: UserTopicStatsRepository,
) {
    private val logger = LoggerFactory.getLogger(ProgressService::class.java)

    /**
     * Returns one [TopicProgressResponse] for every topic
     */
    fun getAllTopicsProgress(userId: Int): List<TopicProgressResponse> {
        val topics = topicRepository.findAll()

        val allTopicProgress = topics.map { topic ->
            val stats = userTopicStatsRepository.findByUserAndTopic(userId, topic.id)
            val totalReviews = stats?.totalReviews ?: 0
            val correctReviews = stats?.correctReviews ?: 0

            TopicProgressResponse(
                topicId = topic.id,
                topicName = topic.name,
                totalCards = cardRepository.countByTopicId(topic.id),
                learnedCards = reviewScheduleRepository.countByUserAndTopic(userId, topic.id),
                dueCards = reviewScheduleRepository.countDueByUserAndTopic(userId, topic.id),
                accuracy = calculateAccuracy(correctReviews, totalReviews),
                currentStreak = stats?.currentStreak ?: 0,
                bestStreak = stats?.bestStreak ?: 0,
            )
        }

        return allTopicProgress
    }

    /**
     * Returns the dashboard payload: the per-topic breakdown plus across-topic aggregates.
     */
    fun getDashboard(userId: Int): DashboardResponse {
        val allTopicsProgress = getAllTopicsProgress(userId)

        val allTopicStats = userTopicStatsRepository.findByUser(userId)
        val totalReviews = allTopicStats.sumOf { it.totalReviews }
        val correctReviews = allTopicStats.sumOf { it.correctReviews }

        val response = DashboardResponse(
            totalReviews = totalReviews,
            correctReviews = correctReviews,
            overallAccuracy = calculateAccuracy(correctReviews, totalReviews),
            currentStreak = allTopicStats.maxOfOrNull { it.currentStreak } ?: 0,
            bestStreak = allTopicStats.maxOfOrNull { it.bestStreak } ?: 0,
            totalDueCards = allTopicsProgress.sumOf { it.dueCards },
            topicProgress = allTopicsProgress,
        )

        logger.debug(
            "Dashboard for userId={}: totalReviews={}, accuracy={}, dueCards={}",
            userId, response.totalReviews, response.overallAccuracy, response.totalDueCards,
        )
        return response
    }

    private fun calculateAccuracy(
        correct: Int,
        total: Int,
    ) : Double {
        return if (total > 0) {
            correct.toDouble() / total
        } else {
            0.0
        }
    }
}