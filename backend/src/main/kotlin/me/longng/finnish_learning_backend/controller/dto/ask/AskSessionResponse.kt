package me.longng.finnish_learning_backend.controller.dto.ask

import me.longng.finnish_learning_backend.domain.ask.AskSession
import java.time.Instant
import java.util.UUID

/**
 * Response of `POST /api/ask/session`. The browser keeps it in `sessionStorage`.
 *
 * @property sessionId sent with every chat message and fit check
 * @property expiresAt when the session ends; the browser then shows the bot check again
 * @property maxMessages chat messages allowed in this session
 */
data class AskSessionResponse(
    val sessionId: UUID,
    val expiresAt: Instant,
    val maxMessages: Int,
) {
    companion object {
        /** The response for a newly created [session]. */
        fun from(session: AskSession, maxMessages: Int) = AskSessionResponse(
            sessionId = session.id,
            expiresAt = session.expiresAt,
            maxMessages = maxMessages,
        )
    }
}