package me.longng.finnish_learning_backend.service

import me.longng.finnish_learning_backend.controller.dto.DashboardResponse
import me.longng.finnish_learning_backend.controller.dto.TopicProgressResponse
import me.longng.finnish_learning_backend.domain.Topic
import me.longng.finnish_learning_backend.domain.UserTopicStats
import me.longng.finnish_learning_backend.persistence.CardRepository
import me.longng.finnish_learning_backend.persistence.ReviewScheduleRepository
import me.longng.finnish_learning_backend.persistence.TopicRepository
import me.longng.finnish_learning_backend.persistence.UserTopicStatsRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import kotlin.test.assertEquals

class ProgressServiceTest {

    private val topicRepository: TopicRepository = mock()
    private val cardRepository: CardRepository = mock()
    private val reviewScheduleRepository: ReviewScheduleRepository = mock()
    private val userTopicStatsRepository: UserTopicStatsRepository = mock()

    private val progressService: ProgressService = ProgressService(
        topicRepository,
        cardRepository,
        reviewScheduleRepository,
        userTopicStatsRepository,
    )

    private val userId = 7
    private val topicId = 1

    private fun topic(
        id: Int = topicId,
        name: String = "Arkielämä"
    ): Topic =
        Topic(id = id, name = name)

    private fun stats(
        totalReviews: Int,
        correctReviews: Int,
        currentStreak: Int = 0,
        bestStreak: Int = 0,
    ): UserTopicStats = UserTopicStats(
        id = 1,
        userId = userId,
        topicId = topicId,
        totalReviews = totalReviews,
        correctReviews = correctReviews,
        currentStreak = currentStreak,
        bestStreak = bestStreak,
        lastReviewedAt = null,
        updatedAt = Instant.now(),
    )

    @Test
    fun testGetAllTopicsProgress_SingleTopic() {
        whenever(topicRepository.findAll()).thenReturn(listOf(topic()))
        whenever(cardRepository.countByTopicId(topicId)).thenReturn(20)
        whenever(reviewScheduleRepository.countByUserAndTopic(userId, topicId)).thenReturn(12)
        whenever(reviewScheduleRepository.countDueByUserAndTopic(userId, topicId)).thenReturn(5)
        whenever(userTopicStatsRepository.findByUserAndTopic(userId, topicId)).thenReturn(
            stats(totalReviews = 10, correctReviews = 8, currentStreak = 3, bestStreak = 6),
        )

        val result = progressService.getAllTopicsProgress(userId)

        val expected = listOf(
            TopicProgressResponse(
                topicId = 1,
                topicName = "Arkielämä",
                totalCards = 20,
                learnedCards = 12,
                dueCards = 5,
                accuracy = 0.8,
                currentStreak = 3,
                bestStreak = 6,
            ),
        )
        assertEquals(expected, result)
    }

    @Test
    fun testGetAllTopicsProgress_NoStats() {
        whenever(topicRepository.findAll()).thenReturn(listOf(topic()))
        whenever(cardRepository.countByTopicId(topicId)).thenReturn(20)
        whenever(reviewScheduleRepository.countByUserAndTopic(userId, topicId)).thenReturn(4)
        whenever(reviewScheduleRepository.countDueByUserAndTopic(userId, topicId)).thenReturn(2)
        whenever(userTopicStatsRepository.findByUserAndTopic(userId, topicId)).thenReturn(null)

        val result = progressService.getAllTopicsProgress(userId)

        val expected = listOf(
            TopicProgressResponse(
                topicId = 1,
                topicName = "Arkielämä",
                totalCards = 20,
                learnedCards = 4,
                dueCards = 2,
                accuracy = 0.0,
                currentStreak = 0,
                bestStreak = 0,
            ),
        )
        assertEquals(expected, result)
    }

    @Test
    fun testGetAllTopicsProgress_ZeroTotalReviews() {
        whenever(topicRepository.findAll()).thenReturn(listOf(topic()))
        whenever(cardRepository.countByTopicId(topicId)).thenReturn(20)
        whenever(reviewScheduleRepository.countByUserAndTopic(userId, topicId)).thenReturn(0)
        whenever(reviewScheduleRepository.countDueByUserAndTopic(userId, topicId)).thenReturn(0)
        whenever(userTopicStatsRepository.findByUserAndTopic(userId, topicId)).thenReturn(
            stats(totalReviews = 0, correctReviews = 0),
        )

        val result = progressService.getAllTopicsProgress(userId)

        val expected = listOf(
            TopicProgressResponse(
                topicId = 1,
                topicName = "Arkielämä",
                totalCards = 20,
                learnedCards = 0,
                dueCards = 0,
                accuracy = 0.0,
                currentStreak = 0,
                bestStreak = 0,
            ),
        )
        assertEquals(expected, result)
    }

    @Test
    fun testGetDashboard_SingleTopic() {
        whenever(topicRepository.findAll()).thenReturn(listOf(topic()))
        whenever(cardRepository.countByTopicId(topicId)).thenReturn(20)
        whenever(reviewScheduleRepository.countByUserAndTopic(userId, topicId)).thenReturn(12)
        whenever(reviewScheduleRepository.countDueByUserAndTopic(userId, topicId)).thenReturn(5)
        whenever(userTopicStatsRepository.findByUserAndTopic(userId, topicId)).thenReturn(
            stats(totalReviews = 10, correctReviews = 8, currentStreak = 3, bestStreak = 6),
        )
        whenever(userTopicStatsRepository.findByUser(userId)).thenReturn(
            listOf(stats(totalReviews = 10, correctReviews = 8, currentStreak = 3, bestStreak = 6)),
        )

        val result = progressService.getDashboard(userId)

        val expectedTopicProgress = TopicProgressResponse(
            topicId = 1,
            topicName = "Arkielämä",
            totalCards = 20,
            learnedCards = 12,
            dueCards = 5,
            accuracy = 0.8,
            currentStreak = 3,
            bestStreak = 6,
        )
        val expected = DashboardResponse(
            totalReviews = 10,
            correctReviews = 8,
            overallAccuracy = 0.8,
            currentStreak = 3,
            bestStreak = 6,
            totalDueCards = 5,
            topicProgress = listOf(expectedTopicProgress),
        )
        assertEquals(expected, result)
    }
}