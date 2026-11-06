package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.persistence.AskSessionRepository
import me.longng.finnish_learning_backend.persistence.AskTurnRepository
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Per-IP limits of the recruiter chatbot, counted from the database, so they survive restarts
 * and work the same in every backend process.
 *
 * - New sessions: at most [AskProperties.Limits.sessionsPerIpPerHour] in any rolling hour.
 * - Messages: at most [AskProperties.Limits.chatPerIpPerDay] chat messages and
 *   [AskProperties.Limits.fitChecksPerIpPerDay] fit checks per Europe/Helsinki calendar day,
 *   across all sessions of the same IP hash. Blocked and failed (ERROR) turns count: a blocked
 *   one still paid for the guardrail, and a failed one may have paid for model calls, e.g. when
 *   the visitor closed the page halfway, so not counting them would let one IP spend without
 *   limit. CAPPED turns don't count: the full budget refused them before any paid call, and
 *   with only a few fit checks a day, a full budget shouldn't use up the visitor's tries.
 *
 * Each check counts first; the caller stores the new session or turn afterwards. That isn't
 * atomic: two requests from one IP at the same moment can both pass, so a limit can be exceeded
 * by a request or two. This is accepted on purpose. These limits stop abuse, they don't guard
 * money (the budget cap has its own lock), and a lock here would make every request wait.
 */
@Component
class AskRateLimiter(
    private val sessionRepository: AskSessionRepository,
    private val turnRepository: AskTurnRepository,
    private val properties: AskProperties,
    private val clock: Clock = Clock.system(HELSINKI_ZONE),
) {

    /**
     * Allows a new session for [ipHash], or refuses it.
     *
     * @throws AskRateLimitedException if the IP already started
     *   [AskProperties.Limits.sessionsPerIpPerHour] sessions in the last hour
     */
    fun checkSession(ipHash: String) {
        val limit = properties.limits.sessionsPerIpPerHour
        val since = clock.instant().minus(SESSION_WINDOW)
        if (sessionRepository.countCreatedSince(ipHash, since) >= limit) {
            throw AskRateLimitedException(AskRateLimitedException.Kind.SESSIONS_PER_HOUR, limit)
        }
    }

    /**
     * Allows one more message of [mode] for [ipHash] today, or refuses it.
     *
     * @throws AskRateLimitedException if the IP already sent today's limit for [mode]
     */
    fun checkMessage(ipHash: String, mode: AskMode) {
        val limits = properties.limits
        val (kind, limit) = when (mode) {
            AskMode.CHAT -> AskRateLimitedException.Kind.CHAT_PER_DAY to limits.chatPerIpPerDay
            AskMode.FIT_CHECK -> AskRateLimitedException.Kind.FIT_CHECKS_PER_DAY to limits.fitChecksPerIpPerDay
        }
        if (turnRepository.countForIpSince(ipHash, mode, startOfToday()) >= limit) {
            throw AskRateLimitedException(kind, limit)
        }
    }

    /** Midnight at the start of today in Helsinki, whatever zone [clock] is in. */
    private fun startOfToday(): Instant =
        LocalDate.ofInstant(clock.instant(), HELSINKI_ZONE).atStartOfDay(HELSINKI_ZONE).toInstant()

    private companion object {
        val HELSINKI_ZONE: ZoneId = ZoneId.of("Europe/Helsinki")
        val SESSION_WINDOW: Duration = Duration.ofHours(1)
    }
}

/**
 * Thrown when a visitor's IP has reached one of the [AskRateLimiter] limits. Mapped to HTTP 429.
 *
 * @property kind which limit was reached
 * @property limit the limit's value, so the visitor can be told how many tries there are
 */
class AskRateLimitedException(
    val kind: Kind,
    val limit: Int,
) : RuntimeException("Limit reached: ${kind.describe(limit)}") {

    /** The limits an [AskRateLimiter] enforces. */
    enum class Kind {
        SESSIONS_PER_HOUR,
        CHAT_PER_DAY,
        FIT_CHECKS_PER_DAY,
        ;

        /** E.g. "3 fit checks per day". */
        fun describe(limit: Int): String = when (this) {
            SESSIONS_PER_HOUR -> "$limit new conversations per hour"
            CHAT_PER_DAY -> "$limit chat messages per day"
            FIT_CHECKS_PER_DAY -> "$limit fit checks per day"
        }
    }
}