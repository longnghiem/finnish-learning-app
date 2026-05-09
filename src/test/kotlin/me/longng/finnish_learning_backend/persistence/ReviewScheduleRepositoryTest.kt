package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.domain.ReviewSchedule
import me.longng.finnish_learning_backend.domain.Role
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class ReviewScheduleRepositoryTest {
    @Autowired
    private lateinit var reviewScheduleRepository: ReviewScheduleRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var cardRepository: CardRepository

    @Autowired
    private lateinit var topicRepository: TopicRepository

    private fun testUserId(): Int = userRepository.insert(
        username = "testuser_${System.nanoTime()}",
        passwordHash = "hashed",
        role = Role.USER,
    ).id

    private fun testCardId(): Int = topicRepository.findAll().first().id.let { topicId ->
        cardRepository.insert(
            topicId = topicId,
            name = "syödä",
            exampleSentence = "Minä syön",
            translation = "to eat",
            imageFilename = "syoda.jpg",
        ).id
    }

    private fun upsertTestSchedule(
        userId: Int,
        cardId: Int,
        repetition: Int = 1,
        easeFactor: Double = 2.5,
        intervalDays: Int = 1,
        nextReviewDate: LocalDate = LocalDate.now(),
    ): ReviewSchedule = reviewScheduleRepository.upsert(
        userId = userId,
        cardId = cardId,
        repetition = repetition,
        easeFactor = easeFactor,
        intervalDays = intervalDays,
        nextReviewDate = nextReviewDate,
    )

    @Test
    fun testUpsert_insert() {
        val userId = testUserId()
        val cardId = testCardId()

        val schedule = reviewScheduleRepository.upsert(
            userId = userId,
            cardId = cardId,
            repetition = 1,
            easeFactor = 2.5,
            intervalDays = 1,
            nextReviewDate = LocalDate.now(),
        )

        assertNotNull(schedule.id)
        assertEquals(userId, schedule.userId)
        assertEquals(cardId, schedule.cardId)
        assertEquals(1, schedule.repetition)
        assertEquals(2.5, schedule.easeFactor)
        assertEquals(1, schedule.intervalDays)
        assertEquals(LocalDate.now(), schedule.nextReviewDate)
    }

    @Test
    fun testUpsert_updatesExistingSchedule() {
        val userId = testUserId()
        val cardId = testCardId()
        val futureDate = LocalDate.now().plusDays(3)

        upsertTestSchedule(userId = userId, cardId = cardId, repetition = 1, intervalDays = 1)

        val updated = reviewScheduleRepository.upsert(
            userId = userId,
            cardId = cardId,
            repetition = 2,
            easeFactor = 2.6,
            intervalDays = 3,
            nextReviewDate = futureDate,
        )

        assertEquals(2, updated.repetition)
        assertEquals(2.6, updated.easeFactor)
        assertEquals(3, updated.intervalDays)
        assertEquals(futureDate, updated.nextReviewDate)
        assertNotNull(updated.lastReviewedAt)
    }

    @Test
    fun testFindByUserAndCard() {
        val userId = testUserId()
        val cardId = testCardId()
        val inserted = upsertTestSchedule(userId = userId, cardId = cardId)

        val found = reviewScheduleRepository.findByUserAndCard(userId, cardId)

        assertNotNull(found)
        assertEquals(inserted.id, found.id)
        assertEquals(inserted.userId, found.userId)
        assertEquals(inserted.cardId, found.cardId)
        assertEquals(inserted.repetition, found.repetition)
        assertEquals(inserted.easeFactor, found.easeFactor)
        assertEquals(inserted.intervalDays, found.intervalDays)
        assertEquals(inserted.nextReviewDate, found.nextReviewDate)
    }

    @Test
    fun testFindByUserAndCard_NotFound() {
        val result = reviewScheduleRepository.findByUserAndCard(userId = 99999, cardId = 99999)

        assertNull(result)
    }

    @Test
    fun testFindDueCards_returnsDueCards() {
        val userId = testUserId()
        val topicId = topicRepository.findAll().first().id
        val card1 = cardRepository.insert(topicId, "juoda", "Hän juo", "to drink", "juoda.jpg")
        val card2 = cardRepository.insert(topicId, "nukkua", "Hän nukkuu", "to sleep", "nukkua.jpg")
        val card3 = cardRepository.insert(topicId, "kävellä", "Hän kävelee", "to walk", "kavella.jpg")

        upsertTestSchedule(userId = userId, cardId = card1.id, nextReviewDate = LocalDate.now().minusDays(1))
        upsertTestSchedule(userId = userId, cardId = card2.id, nextReviewDate = LocalDate.now())
        upsertTestSchedule(userId = userId, cardId = card3.id, nextReviewDate = LocalDate.now().plusDays(5))

        val dueCards = reviewScheduleRepository.findDueCards(userId = userId, topicId = topicId, limit = 10)

        assertEquals(2, dueCards.size)
        assertTrue(dueCards.map { it.cardId }.containsAll(listOf(card1.id, card2.id)))
    }

    @Test
    fun testFindDueCards_respectsLimit() {
        val userId = testUserId()
        val topicId = topicRepository.findAll().first().id
        val card1 = cardRepository.insert(topicId, "juoda", "Hän juo", "to drink", "juoda.jpg")
        val card2 = cardRepository.insert(topicId, "nukkua", "Hän nukkuu", "to sleep", "nukkua.jpg")
        val card3 = cardRepository.insert(topicId, "kävellä", "Hän kävelee", "to walk", "kavella.jpg")

        upsertTestSchedule(userId = userId, cardId = card1.id, nextReviewDate = LocalDate.now().minusDays(2))
        upsertTestSchedule(userId = userId, cardId = card2.id, nextReviewDate = LocalDate.now().minusDays(1))
        upsertTestSchedule(userId = userId, cardId = card3.id, nextReviewDate = LocalDate.now())

        val dueCards = reviewScheduleRepository.findDueCards(userId = userId, topicId = topicId, limit = 2)

        assertEquals(2, dueCards.size)
    }

    @Test
    fun testCountByUserAndTopic() {
        val userId = testUserId()
        val topicId = topicRepository.findAll().first().id
        val card1 = cardRepository.insert(topicId, "juoda", "Hän juo", "to drink", "juoda.jpg")
        val card2 = cardRepository.insert(topicId, "nukkua", "Hän nukkuu", "to sleep", "nukkua.jpg")

        upsertTestSchedule(userId = userId, cardId = card1.id)
        upsertTestSchedule(userId = userId, cardId = card2.id)

        val count = reviewScheduleRepository.countByUserAndTopic(userId = userId, topicId = topicId)

        assertEquals(2, count)
    }

    @Test
    fun testCountByUserAndTopic_noReviews() {
        val userId = testUserId()
        val topicId = topicRepository.findAll().first().id

        val count = reviewScheduleRepository.countByUserAndTopic(userId = userId, topicId = topicId)

        assertEquals(0, count)
    }

    @Test
    fun testCountDueByUserAndTopic() {
        val userId = testUserId()
        val topicId = topicRepository.findAll().first().id
        val card1 = cardRepository.insert(topicId, "juoda", "Hän juo", "to drink", "juoda.jpg")
        val card2 = cardRepository.insert(topicId, "nukkua", "Hän nukkuu", "to sleep", "nukkua.jpg")
        val card3 = cardRepository.insert(topicId, "kävellä", "Hän kävelee", "to walk", "kavella.jpg")

        upsertTestSchedule(userId = userId, cardId = card1.id, nextReviewDate = LocalDate.now().minusDays(1))
        upsertTestSchedule(userId = userId, cardId = card2.id, nextReviewDate = LocalDate.now())
        upsertTestSchedule(userId = userId, cardId = card3.id, nextReviewDate = LocalDate.now().plusDays(3))

        val count = reviewScheduleRepository.countDueByUserAndTopic(userId = userId, topicId = topicId)

        assertEquals(2, count)
    }

    @Test
    fun testCountDueByUserAndTopic_noDue() {
        val userId = testUserId()
        val topicId = topicRepository.findAll().first().id
        val card = cardRepository.insert(topicId, "juoda", "Hän juo", "to drink", "juoda.jpg")

        upsertTestSchedule(userId = userId, cardId = card.id, nextReviewDate = LocalDate.now().plusDays(1))

        val count = reviewScheduleRepository.countDueByUserAndTopic(userId = userId, topicId = topicId)

        assertEquals(0, count)
    }
}

