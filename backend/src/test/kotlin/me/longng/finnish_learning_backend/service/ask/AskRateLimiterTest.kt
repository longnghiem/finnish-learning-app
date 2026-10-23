package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.config.testAskProperties
import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.persistence.AskSessionRepository
import me.longng.finnish_learning_backend.persistence.AskTurnRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class AskRateLimiterTest {

    companion object {
        private const val IP_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        private val LIMITS = AskProperties.Limits(chatPerIpPerDay = 30, fitChecksPerIpPerDay = 3, sessionsPerIpPerHour = 5)

        /** 2026-10-22 00:30 in Helsinki (summer time, UTC+3), which is still 2026-10-21 in UTC. */
        private val JUST_AFTER_HELSINKI_MIDNIGHT: Instant = Instant.parse("2026-10-21T21:30:00Z")
    }

    private val sessionRepository: AskSessionRepository = mock()
    private val turnRepository: AskTurnRepository = mock()

    // A UTC clock on purpose: the limiter must use Helsinki days whatever the clock's zone is.
    private val limiter = AskRateLimiter(
        sessionRepository = sessionRepository,
        turnRepository = turnRepository,
        properties = testAskProperties(limits = LIMITS),
        clock = Clock.fixed(JUST_AFTER_HELSINKI_MIDNIGHT, ZoneOffset.UTC),
    )

    @Test
    fun testCheckSession_UnderLimit() {
        whenever(sessionRepository.countCreatedSince(eq(IP_HASH), any())).thenReturn(LIMITS.sessionsPerIpPerHour - 1)

        limiter.checkSession(IP_HASH)

        // The window is the last hour.
        verify(sessionRepository).countCreatedSince(IP_HASH, Instant.parse("2026-10-21T20:30:00Z"))
    }

    @Test
    fun testCheckSession_AtLimitRejected() {
        whenever(sessionRepository.countCreatedSince(eq(IP_HASH), any())).thenReturn(LIMITS.sessionsPerIpPerHour)

        val error = assertThrows<AskRateLimitedException> { limiter.checkSession(IP_HASH) }

        assertEquals(AskRateLimitedException.Kind.SESSIONS_PER_HOUR, error.kind)
        assertEquals(LIMITS.sessionsPerIpPerHour, error.limit)
    }

    @Test
    fun testCheckMessage_UnderLimit() {
        whenever(turnRepository.countForIpSince(eq(IP_HASH), eq(AskMode.CHAT), any())).thenReturn(LIMITS.chatPerIpPerDay - 1)

        limiter.checkMessage(IP_HASH, AskMode.CHAT)
    }

    @Test
    fun testCheckMessage_OverLimit() {
        whenever(turnRepository.countForIpSince(eq(IP_HASH), eq(AskMode.CHAT), any())).thenReturn(LIMITS.chatPerIpPerDay)

        val error = assertThrows<AskRateLimitedException> { limiter.checkMessage(IP_HASH, AskMode.CHAT) }

        assertEquals(AskRateLimitedException.Kind.CHAT_PER_DAY, error.kind)
        assertEquals("Limit reached: 30 chat messages per day", error.message)
    }

    @Test
    fun testCheckMessage_FitChecksHaveTheirOwnLimit() {
        // 3 fit checks used up; 3 chat messages are far from the chat limit.
        whenever(turnRepository.countForIpSince(eq(IP_HASH), any(), any())).thenReturn(LIMITS.fitChecksPerIpPerDay)

        val error = assertThrows<AskRateLimitedException> { limiter.checkMessage(IP_HASH, AskMode.FIT_CHECK) }
        limiter.checkMessage(IP_HASH, AskMode.CHAT)

        assertEquals(AskRateLimitedException.Kind.FIT_CHECKS_PER_DAY, error.kind)
        assertEquals(LIMITS.fitChecksPerIpPerDay, error.limit)
    }

    @Test
    fun testCheckMessage_ResetsAtHelsinkiMidnight() {
        whenever(turnRepository.countForIpSince(eq(IP_HASH), eq(AskMode.CHAT), any())).thenReturn(0)

        limiter.checkMessage(IP_HASH, AskMode.CHAT)

        // Counting starts at Helsinki midnight (21:00 UTC the day before), not at UTC midnight.
        verify(turnRepository).countForIpSince(IP_HASH, AskMode.CHAT, Instant.parse("2026-10-21T21:00:00Z"))
    }
}