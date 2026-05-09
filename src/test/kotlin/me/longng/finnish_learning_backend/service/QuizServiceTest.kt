package me.longng.finnish_learning_backend.service

import me.longng.finnish_learning_backend.domain.Card
import me.longng.finnish_learning_backend.domain.ReviewSchedule
import me.longng.finnish_learning_backend.event.QuizAnswerEvent
import me.longng.finnish_learning_backend.event.QuizEventProducer
import me.longng.finnish_learning_backend.persistence.CardRepository
import me.longng.finnish_learning_backend.persistence.ReviewScheduleRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuizServiceTest {

    private val cardRepository: CardRepository = mock()
    private val reviewScheduleRepository: ReviewScheduleRepository = mock()
    private val quizEventProducer: QuizEventProducer = mock()

    private val quizService = QuizService(cardRepository, reviewScheduleRepository, quizEventProducer)

    private val testCard = Card(
        id = 42,
        topicId = 3,
        name = "puhua",
        exampleSentence = "Minä puhun suomea",
        translation = "to speak",
        imageFilename = "puhua.png",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Test
    fun testGetQuizCards_InvalidLimit() {
        assertThrows<IllegalArgumentException> {
            quizService.getQuizCards(userId = 1, topicId = 1, limit = 0)
        }
    }

    @Test
    fun testGetQuizCards_DueCardsFirst() {
        val schedule = ReviewSchedule(
            id = 1,
            userId = 1,
            cardId = 42,
            repetition = 2,
            easeFactor = 2.5,
            intervalDays = 6,
            nextReviewDate = LocalDate.now().minusDays(1),
            lastReviewedAt = Instant.now(),
        )
        whenever(reviewScheduleRepository.findDueCards(
            userId = 1,
            topicId = 3,
            limit = 5)
        ).thenReturn(listOf(schedule))
        whenever(cardRepository.findById(42)).thenReturn(testCard)
        whenever(cardRepository.findNewCardsForUser(
            userId = 1,
            topicId = 3,
            limit = 4
        )).thenReturn(emptyList())

        val result = quizService.getQuizCards(userId = 1, topicId = 3, limit = 5)

        assertEquals(1, result.size)
        assertFalse(result[0].isNew)
        assertEquals(42, result[0].cardId)
        assertEquals(2, result[0].repetition)
    }

    @Test
    fun testGetQuizCards_FillsWithNewCards() {
        whenever(reviewScheduleRepository.findDueCards(
            userId = 1,
            topicId = 3,
            limit = 5
        )).thenReturn(emptyList())
        whenever(cardRepository.findNewCardsForUser(
            userId = 1,
            topicId = 3,
            limit = 5
        )).thenReturn(listOf(testCard))

        val result = quizService.getQuizCards(userId = 1, topicId = 3, limit = 5)

        assertEquals(1, result.size)
        assertTrue(result[0].isNew)
        assertNull(result[0].repetition)
        assertNull(result[0].nextReviewDate)
    }

    @Test
    fun testGetQuizCards_NoNewCardsWhenLimitFilled() {
        // Due cards fill the entire limit
        val schedules = (1..3).map { i ->
            ReviewSchedule(
                id = i,
                userId = 1,
                cardId = i,
                repetition = 1,
                easeFactor = 2.5,
                intervalDays = 1,
                nextReviewDate = LocalDate.now().minusDays(1),
                lastReviewedAt = Instant.now(),
            )
        }
        whenever(reviewScheduleRepository.findDueCards(
            userId = 1,
            topicId = 3,
            limit = 3
        )).thenReturn(schedules)
        schedules.forEach { schedule ->
            whenever(cardRepository.findById(schedule.cardId)).thenReturn(
                testCard.copy(id = schedule.cardId),
            )
        }

        val result = quizService.getQuizCards(userId = 1, topicId = 3, limit = 3)

        assertEquals(3, result.size)
        // findNewCardsForUser should NOT be called when limit is filled
        verify(cardRepository, never()).findNewCardsForUser(any(), any(), any())
    }

    @Test
    fun testSubmitAnswer_InvalidQuality() {
        assertThrows<IllegalArgumentException> {
            quizService.submitAnswer(userId = 1, cardId = 42, quality = 6)
        }
    }

    @Test
    fun testSubmitAnswer_CardNotFound() {
        assertThrows<CardNotFoundException> {
            quizService.submitAnswer(userId = 1, cardId = 99, quality = 4)
        }
    }

    @Test
    fun testSubmitAnswer_FirstReviewCorrect() {
        whenever(cardRepository.findById(42)).thenReturn(testCard)
        whenever(reviewScheduleRepository.findByUserAndCard(
            userId = 1,
            cardId = 42
        )).thenReturn(null) // First review
        whenever(reviewScheduleRepository.upsert(any(), any(), any(), any(), any(), any()))
            .thenReturn(
                ReviewSchedule(
                    id = 1,
                    userId = 1,
                    cardId = 42,
                    repetition = 1,
                    easeFactor = 2.5,
                    intervalDays = 1,
                    nextReviewDate = LocalDate.now().plusDays(1),
                    lastReviewedAt = Instant.now()
                ),
            )

        val result = quizService.submitAnswer(userId = 1, cardId = 42, quality = 4)

        assertTrue(result.correct)
        assertEquals(1, result.repetition)
        assertEquals(1, result.intervalDays)

        // Verify Kafka event content
        val eventCaptor = argumentCaptor<QuizAnswerEvent>()
        verify(quizEventProducer).publish(eventCaptor.capture())
        val event = eventCaptor.firstValue
        assertEquals(1, event.userId)
        assertEquals(42, event.cardId)
        assertEquals(3, event.topicId)
        assertEquals(4, event.quality)
        assertTrue(event.correct)
    }

    @Test
    fun testSubmitAnswer_IncorrectResetsSchedule() {
        whenever(cardRepository.findById(42)).thenReturn(testCard)
        whenever(reviewScheduleRepository.findByUserAndCard(
            userId = 1,
            cardId = 42
        )).thenReturn(
            ReviewSchedule(
                id = 1,
                userId = 1,
                cardId = 42,
                repetition = 3,
                easeFactor = 2.5,
                intervalDays = 15,
                nextReviewDate = LocalDate.now(),
                lastReviewedAt = Instant.now()
            ),
        )
        whenever(reviewScheduleRepository.upsert(any(), any(), any(), any(), any(), any()))
            .thenReturn(
                ReviewSchedule(
                    id = 1,
                    userId = 1,
                    cardId = 42,
                    repetition = 0,
                    easeFactor = 1.96,
                    intervalDays = 1,
                    nextReviewDate = LocalDate.now().plusDays(1),
                    lastReviewedAt = Instant.now()
                ),
            )

        val result = quizService.submitAnswer(userId = 1, cardId = 42, quality = 1)

        assertFalse(result.correct)
        assertEquals(0, result.repetition)
        assertEquals(1, result.intervalDays)

        // Verify Kafka event marks as incorrect
        val eventCaptor = argumentCaptor<QuizAnswerEvent>()
        verify(quizEventProducer).publish(eventCaptor.capture())
        assertFalse(eventCaptor.firstValue.correct)
    }
}