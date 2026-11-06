package me.longng.finnish_learning_backend.controller.dto.ask

import java.util.UUID

/**
 * Request body for `POST /api/ask/fit-check`.
 *
 * @property sessionId the id from `POST /api/ask/session`
 * @property jobDescription the job description the recruiter pasted
 */
data class AskFitCheckRequest(
    val sessionId: UUID,
    val jobDescription: String,
)