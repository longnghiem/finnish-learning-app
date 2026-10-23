package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.domain.ask.AskSession
import me.longng.finnish_learning_backend.persistence.AskSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.InetAddress
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Creates and checks the anonymous chat sessions of the recruiter chatbot.
 *
 * A visitor gets a session after passing the Turnstile bot check, and sends its id with every
 * chat message. So the bot check runs once per conversation, not once per message.
 *
 * **Why a session is bound to the IP hash:** the id lives in the browser's `sessionStorage`. If
 * it leaks (a shared screenshot, a malicious browser extension), it is useless from another
 * network. The cost: a visitor whose address changes mid-conversation (Wi-Fi to mobile data, or
 * IPv4 to IPv6) has to pass the bot check again and starts a new conversation. Such rejections
 * are logged with the reason, so how often it happens can be measured.
 *
 * **Why sessions are short** ([AskProperties.Session.ttlMinutes], 60 minutes): long enough for a
 * recruiter's conversation, short enough that a leaked id soon stops working. Together with the
 * message limit per session, it means a bot that solved the check once can't use the session for
 * long.
 *
 * The session id is a random UUID: 122 random bits from a secure random generator, so ids can't
 * be guessed.
 */
@Service
class AskSessionService(
    private val sessionRepository: AskSessionRepository,
    private val ipHasher: IpHasher,
    private val rateLimiter: AskRateLimiter,
    private val turnstileClient: TurnstileClient,
    private val properties: AskProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(AskSessionService::class.java)

    /**
     * Creates a session for the visitor at [clientIp], after the per-IP limit and the bot check.
     *
     * The limit is checked first: an IP that is over its limit gets a quick 429 without a call to
     * Cloudflare.
     *
     * @throws AskRateLimitedException if this IP already started too many sessions this hour
     * @throws AskSessionInvalidException with [AskSessionInvalidException.Reason.TURNSTILE_FAILED]
     *   if Cloudflare rejected [turnstileToken]
     * @throws TurnstileVerificationException if the token couldn't be checked at all
     * @throws IllegalArgumentException if [turnstileToken] is blank or too long
     */
    fun create(turnstileToken: String, clientIp: InetAddress): AskSession {
        val ipHash = ipHasher.hash(clientIp)
        rateLimiter.checkSession(ipHash)
        if (!turnstileClient.verify(turnstileToken, clientIp)) {
            throw AskSessionInvalidException(AskSessionInvalidException.Reason.TURNSTILE_FAILED)
        }
        val expiresAt = clock.instant().plus(Duration.ofMinutes(properties.session.ttlMinutes))
        val session = sessionRepository.create(UUID.randomUUID(), ipHash, expiresAt)
        logger.info("Ask session created: id={}", session.id)
        return session
    }

    /**
     * Returns the session [sessionId] if the visitor at [clientIp] may use it: it exists, hasn't
     * expired, and was created from the same IP hash.
     *
     * All three failures look the same to the visitor: they run the bot check again and get a new
     * session. Telling them apart would only help someone probing stolen ids.
     *
     * @throws AskSessionInvalidException with [AskSessionInvalidException.Reason.SESSION_EXPIRED]
     *   otherwise
     */
    fun validate(sessionId: UUID, clientIp: InetAddress): AskSession {
        val session = sessionRepository.findById(sessionId)
        val problem = when {
            session == null -> "unknown"
            session.isExpiredAt(clock.instant()) -> "expired"
            session.ipHash != ipHasher.hash(clientIp) -> "ip-changed"
            else -> return session
        }
        logger.info("Ask session rejected: id={} reason={}", sessionId, problem)
        throw AskSessionInvalidException(AskSessionInvalidException.Reason.SESSION_EXPIRED)
    }
}

/**
 * Thrown when a visitor can't get or use a chat session (see [AskSessionService]).
 *
 * @property reason why; its name is the error code the frontend reacts to
 */
class AskSessionInvalidException(
    val reason: Reason,
) : RuntimeException(reason.text) {

    /** Why a session was refused. */
    enum class Reason(val text: String) {
        /** Cloudflare rejected the bot-check token. Mapped to HTTP 403. */
        TURNSTILE_FAILED("The bot check failed. Please try again."),

        /** The session is unknown, expired, or used from another network. Mapped to HTTP 401. */
        SESSION_EXPIRED("The chat session has ended. Please start a new one."),
    }
}