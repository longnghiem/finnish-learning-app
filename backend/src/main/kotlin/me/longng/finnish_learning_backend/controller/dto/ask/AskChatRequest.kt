package me.longng.finnish_learning_backend.controller.dto.ask

import java.util.UUID

/**
 * Request body for `POST /api/ask/chat`.
 *
 * @property sessionId the id from `POST /api/ask/session`
 * @property message the recruiter's question
 */
data class AskChatRequest(
    val sessionId: UUID,
    val message: String,
)