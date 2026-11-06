package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.config.testAskProperties
import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.persistence.AskDailyStatsRepository
import me.longng.finnish_learning_backend.persistence.AskSessionRepository
import me.longng.finnish_learning_backend.persistence.AskTurnRepository
import me.longng.finnish_learning_backend.persistence.randomIpHash
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.scheduling.config.CronTask
import org.springframework.scheduling.config.ScheduledTaskHolder
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [AskRetentionJob] on the real repositories and database. The job's clock is moved forward
 * instead of making old sessions: the database sets `created_at` itself.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class AskRetentionJobTest {

    @Autowired
    private lateinit var sessionRepository: AskSessionRepository

    @Autowired
    private lateinit var turnRepository: AskTurnRepository

    @Autowired
    private lateinit var statsRepository: AskDailyStatsRepository

    @Autowired
    private lateinit var scheduledTaskHolder: ScheduledTaskHolder

    /** A job whose clock says [daysLater] days from now. */
    private fun jobRunning(daysLater: Long) = AskRetentionJob(
        sessionRepository = sessionRepository,
        properties = testAskProperties(),
        clock = Clock.fixed(Instant.now().plus(Duration.ofDays(daysLater)), ZoneOffset.UTC),
    )

    private fun newSessionWithTurn(): UUID {
        val id = UUID.randomUUID()
        sessionRepository.create(id, randomIpHash(), Instant.now().plus(Duration.ofHours(1)))
        turnRepository.insertPending(id, AskMode.CHAT, "Has Long used Kafka?")
        return id
    }

    @Test
    fun testPurge_DeletesOlderThan30Days() {
        val id = newSessionWithTurn()

        jobRunning(daysLater = 31).purge()

        assertNull(sessionRepository.findById(id))
        assertEquals(emptyList(), turnRepository.findBySession(id), "the turns go with their session")
    }

    @Test
    fun testPurge_KeepsYoungerThan30Days() {
        val id = newSessionWithTurn()

        jobRunning(daysLater = 29).purge()

        assertNotNull(sessionRepository.findById(id))
        assertEquals(1, turnRepository.findBySession(id).size)
    }

    @Test
    fun testPurge_KeepsDailyStats() {
        val day = LocalDate.of(2098, 10, 15)
        statsRepository.incrementCounters(day, answers = 3, blocked = 1)
        newSessionWithTurn()

        jobRunning(daysLater = 31).purge()

        assertEquals(3, statsRepository.findByDay(day)?.answers, "the totals hold no personal data")
    }

    @Test
    fun testPurge_ScheduledNightly() {
        val cronTasks = scheduledTaskHolder.scheduledTasks.mapNotNull { it.task as? CronTask }

        assertTrue(cronTasks.any { it.expression == "0 15 3 * * *" }, "the purge runs at 03:15 every night")
    }
}