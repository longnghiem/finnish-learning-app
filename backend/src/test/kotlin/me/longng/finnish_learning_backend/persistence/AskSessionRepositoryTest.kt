package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.domain.ask.AskMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A random value shaped like a real IP hash (64 hex characters), so tests never share one. */
internal fun randomIpHash(): String = (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "")

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class AskSessionRepositoryTest {

    @Autowired
    private lateinit var sessionRepository: AskSessionRepository

    @Autowired
    private lateinit var turnRepository: AskTurnRepository

    private val ipHash = randomIpHash()

    @Test
    fun testCreate_FindByIdReturnsSession() {
        val id = UUID.randomUUID()
        // timestamptz stores microseconds and Instant.now() can carry nanoseconds, so the test
        // uses a value the database can store exactly.
        val expiresAt = Instant.now().plus(Duration.ofHours(2)).truncatedTo(ChronoUnit.MICROS)

        val created = sessionRepository.create(id, ipHash, expiresAt)
        val found = sessionRepository.findById(id)

        assertNotNull(found)
        assertEquals(created, found)
        assertEquals(ipHash, found.ipHash)
        assertEquals(expiresAt, found.expiresAt)
    }

    @Test
    fun testFindById_Unknown() {
        assertNull(sessionRepository.findById(UUID.randomUUID()))
    }

    @Test
    fun testCreate_WrongIpHashLengthRejected() {
        val expiresAt = Instant.now().plus(Duration.ofHours(2))

        assertThrows<IllegalArgumentException> { sessionRepository.create(UUID.randomUUID(), "", expiresAt) }
        assertThrows<IllegalArgumentException> { sessionRepository.create(UUID.randomUUID(), ipHash + "0", expiresAt) }
    }

    @Test
    fun testIsExpiredAt_FromExpiryOn() {
        val expiresAt = Instant.now().plus(Duration.ofHours(2))
        val session = sessionRepository.create(UUID.randomUUID(), ipHash, expiresAt)

        assertFalse(session.isExpiredAt(session.expiresAt.minusSeconds(1)))
        assertTrue(session.isExpiredAt(session.expiresAt))
    }

    @Test
    fun testCountCreatedSince_OnlyMatchingIpAndWindow() {
        val expiresAt = Instant.now().plus(Duration.ofHours(2))
        sessionRepository.create(UUID.randomUUID(), ipHash, expiresAt)
        sessionRepository.create(UUID.randomUUID(), ipHash, expiresAt)
        sessionRepository.create(UUID.randomUUID(), randomIpHash(), expiresAt)

        assertEquals(2, sessionRepository.countCreatedSince(ipHash, Instant.now().minus(Duration.ofHours(1))))
        assertEquals(0, sessionRepository.countCreatedSince(ipHash, Instant.now().plus(Duration.ofHours(1))))
    }

    @Test
    fun testDeleteById_Found() {
        val id = UUID.randomUUID()
        sessionRepository.create(id, ipHash, Instant.now().plus(Duration.ofHours(2)))

        assertTrue(sessionRepository.deleteById(id))
        assertNull(sessionRepository.findById(id))
    }

    @Test
    fun testDeleteById_Unknown() {
        assertFalse(sessionRepository.deleteById(UUID.randomUUID()))
    }

    @Test
    fun testDeleteCreatedBefore_CascadesTurns() {
        val id = UUID.randomUUID()
        sessionRepository.create(id, ipHash, Instant.now().plus(Duration.ofHours(2)))
        turnRepository.insertPending(id, AskMode.CHAT, "Hei")

        // A cutoff in the past keeps the session; one in the future deletes it. The counts are
        // "at least", because sessions committed by other test classes may be deleted too.
        sessionRepository.deleteCreatedBefore(Instant.now().minus(Duration.ofDays(30)))
        assertNotNull(sessionRepository.findById(id))
        assertEquals(1, turnRepository.findBySession(id).size)

        assertTrue(sessionRepository.deleteCreatedBefore(Instant.now().plus(Duration.ofMinutes(1))) >= 1)
        assertNull(sessionRepository.findById(id))
        assertEquals(emptyList(), turnRepository.findBySession(id))
    }
}