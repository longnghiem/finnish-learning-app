package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.domain.Role
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class UserTopicStatsRepositoryTest {
    @Autowired
    private lateinit var userTopicStatsRepository: UserTopicStatsRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var topicRepository: TopicRepository

    private fun testUserId(): Int = userRepository.insert(
        username = "testuser_${System.nanoTime()}",
        passwordHash = "hashed",
        role = Role.USER,
    ).id

    private fun firstTopicId(): Int = topicRepository.findAll().first().id

    private fun secondTopicId(): Int = topicRepository.findAll()[1].id

    @Test
    fun testUpsert_createsRowOnFirstAnswer() {
        val userId = testUserId()
        val topicId = firstTopicId()

        userTopicStatsRepository.upsert(userId = userId, topicId = topicId, correct = true)

        val stats = userTopicStatsRepository.findByUserAndTopic(userId, topicId)

        assertNotNull(stats)
        assertEquals(userId, stats.userId)
        assertEquals(topicId, stats.topicId)
        assertEquals(1, stats.totalReviews)
        assertEquals(1, stats.correctReviews)
        assertEquals(1, stats.currentStreak)
        assertEquals(1, stats.bestStreak)
        assertNotNull(stats.lastReviewedAt)
        assertNotNull(stats.updatedAt)
    }

    @Test
    fun testUpsert_incrementsCountersOnSubsequentAnswers() {
        val userId = testUserId()
        val topicId = firstTopicId()

        userTopicStatsRepository.upsert(userId = userId, topicId = topicId, correct = true)
        userTopicStatsRepository.upsert(userId = userId, topicId = topicId, correct = true)
        userTopicStatsRepository.upsert(userId = userId, topicId = topicId, correct = false)

        val stats = userTopicStatsRepository.findByUserAndTopic(userId, topicId)

        assertNotNull(stats)
        assertEquals(3, stats.totalReviews)
        assertEquals(2, stats.correctReviews)
    }

    @Test
    fun testUpsert_streakIncrementsOnCorrectAnswers() {
        val userId = testUserId()
        val topicId = firstTopicId()

        userTopicStatsRepository.upsert(userId = userId, topicId = topicId, correct = true)
        userTopicStatsRepository.upsert(userId = userId, topicId = topicId, correct = true)
        userTopicStatsRepository.upsert(userId = userId, topicId = topicId, correct = true)

        val stats = userTopicStatsRepository.findByUserAndTopic(userId, topicId)

        assertNotNull(stats)
        assertEquals(3, stats.currentStreak)
        assertEquals(3, stats.bestStreak)
    }

    @Test
    fun testUpsert_incorrectAnswerResetsCurrentStreak() {
        val userId = testUserId()
        val topicId = firstTopicId()

        userTopicStatsRepository.upsert(userId = userId, topicId = topicId, correct = true)
        userTopicStatsRepository.upsert(userId = userId, topicId = topicId, correct = true)
        userTopicStatsRepository.upsert(userId = userId, topicId = topicId, correct = false)

        val stats = userTopicStatsRepository.findByUserAndTopic(userId, topicId)

        assertNotNull(stats)
        assertEquals(0, stats.currentStreak)
    }

    @Test
    fun testUpsert_bestStreakPreservedAfterReset() {
        val userId = testUserId()
        val topicId = firstTopicId()

        userTopicStatsRepository.upsert(userId = userId, topicId = topicId, correct = true)
        userTopicStatsRepository.upsert(userId = userId, topicId = topicId, correct = true)
        userTopicStatsRepository.upsert(userId = userId, topicId = topicId, correct = false)
        userTopicStatsRepository.upsert(userId = userId, topicId = topicId, correct = true)

        val stats = userTopicStatsRepository.findByUserAndTopic(userId, topicId)

        assertNotNull(stats)
        assertEquals(1, stats.currentStreak)
        assertEquals(2, stats.bestStreak)
    }

    @Test
    fun testFindByUserAndTopic_NotFound() {
        val result = userTopicStatsRepository.findByUserAndTopic(userId = 99999, topicId = 99999)

        assertNull(result)
    }

    @Test
    fun testFindByUser_returnsAllTopicsForUser() {
        val userId = testUserId()
        val topicA = firstTopicId()
        val topicB = secondTopicId()

        userTopicStatsRepository.upsert(userId = userId, topicId = topicA, correct = true)
        userTopicStatsRepository.upsert(userId = userId, topicId = topicB, correct = false)

        val statsList = userTopicStatsRepository.findByUser(userId)

        assertEquals(2, statsList.size)
        assertTrue(statsList.map { it.topicId }.containsAll(listOf(topicA, topicB)))
    }

    @Test
    fun testFindByUser_returnsEmptyListWhenNoStudy() {
        val userId = testUserId()

        val statsList = userTopicStatsRepository.findByUser(userId)

        assertTrue(statsList.isEmpty())
    }

    @Test
    fun testFindByUser_isolatedFromOtherUsers() {
        val userId1 = testUserId()
        val userId2 = testUserId()
        val topicId = firstTopicId()

        userTopicStatsRepository.upsert(userId = userId1, topicId = topicId, correct = true)

        val statsForUser2 = userTopicStatsRepository.findByUser(userId2)

        assertTrue(statsForUser2.isEmpty())
    }
}

