package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.testAskProperties
import me.longng.finnish_learning_backend.domain.ask.AskSession
import me.longng.finnish_learning_backend.persistence.AskSessionRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.net.InetAddress
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals

class AskSessionServiceTest {

    companion object {
        private val NOW: Instant = Instant.parse("2026-10-22T09:00:00Z")
        private val VISITOR: InetAddress = InetAddress.getByName("203.0.113.7")
        private val OTHER_NETWORK: InetAddress = InetAddress.getByName("198.51.100.4")
        private const val TOKEN = "token-from-the-widget"
    }

    private val properties = testAskProperties()

    // The real hasher (it is pure code); the database, the limiter and Cloudflare are mocked.
    private val ipHasher = IpHasher(properties)
    private val sessionRepository: AskSessionRepository = mock()
    private val rateLimiter: AskRateLimiter = mock()
    private val turnstileClient: TurnstileClient = mock()

    private val service = AskSessionService(
        sessionRepository = sessionRepository,
        ipHasher = ipHasher,
        rateLimiter = rateLimiter,
        turnstileClient = turnstileClient,
        properties = properties,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private fun storedSession(ipHash: String = ipHasher.hash(VISITOR), expiresAt: Instant = NOW.plusSeconds(60)) =
        AskSession(id = UUID.randomUUID(), ipHash = ipHash, createdAt = NOW.minusSeconds(60), expiresAt = expiresAt)

    @Test
    fun testCreate_Success() {
        val ipHash = ipHasher.hash(VISITOR)
        val expiresAt = NOW.plus(Duration.ofMinutes(properties.session.ttlMinutes))
        whenever(turnstileClient.verify(TOKEN, VISITOR)).thenReturn(true)
        whenever(sessionRepository.create(any(), eq(ipHash), eq(expiresAt))).thenAnswer { call ->
            AskSession(id = call.getArgument(0), ipHash = ipHash, createdAt = NOW, expiresAt = expiresAt)
        }

        val session = service.create(TOKEN, VISITOR)

        assertEquals(ipHash, session.ipHash)
        assertEquals(Instant.parse("2026-10-22T10:00:00Z"), session.expiresAt)
        verify(rateLimiter).checkSession(ipHash)
    }

    @Test
    fun testCreate_RateLimited() {
        doThrow(AskRateLimitedException(AskRateLimitedException.Kind.SESSIONS_PER_HOUR, 5))
            .whenever(rateLimiter).checkSession(any())

        assertThrows<AskRateLimitedException> { service.create(TOKEN, VISITOR) }

        // Refused before Cloudflare is asked and before anything is stored.
        verifyNoInteractions(turnstileClient)
        verify(sessionRepository, never()).create(any(), any(), any())
    }

    @Test
    fun testCreate_TurnstileFailed() {
        whenever(turnstileClient.verify(TOKEN, VISITOR)).thenReturn(false)

        val error = assertThrows<AskSessionInvalidException> { service.create(TOKEN, VISITOR) }

        assertEquals(AskSessionInvalidException.Reason.TURNSTILE_FAILED, error.reason)
        verify(sessionRepository, never()).create(any(), any(), any())
    }

    @Test
    fun testValidate_Valid() {
        val session = storedSession()
        whenever(sessionRepository.findById(session.id)).thenReturn(session)

        assertEquals(session, service.validate(session.id, VISITOR))
    }

    @Test
    fun testValidate_Expired() {
        // Expired from expiresAt on, not one moment later.
        val session = storedSession(expiresAt = NOW)
        whenever(sessionRepository.findById(session.id)).thenReturn(session)

        val error = assertThrows<AskSessionInvalidException> { service.validate(session.id, VISITOR) }

        assertEquals(AskSessionInvalidException.Reason.SESSION_EXPIRED, error.reason)
    }

    @Test
    fun testValidate_IpMismatch() {
        val session = storedSession()
        whenever(sessionRepository.findById(session.id)).thenReturn(session)

        val error = assertThrows<AskSessionInvalidException> { service.validate(session.id, OTHER_NETWORK) }

        assertEquals(AskSessionInvalidException.Reason.SESSION_EXPIRED, error.reason)
    }

    @Test
    fun testValidate_Unknown() {
        val error = assertThrows<AskSessionInvalidException> { service.validate(UUID.randomUUID(), VISITOR) }

        assertEquals(AskSessionInvalidException.Reason.SESSION_EXPIRED, error.reason)
    }
}