package me.longng.finnish_learning_backend.controller.dto.ask

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import me.longng.finnish_learning_backend.service.ask.TurnstileClient

/**
 * Request body for `POST /api/ask/session`.
 *
 * @property turnstileToken the token the Turnstile widget gave the browser
 */
data class CreateAskSessionRequest(
    @field:NotBlank(message = "Turnstile token must not be blank")
    @field:Size(max = TurnstileClient.MAX_TOKEN_LENGTH, message = "Turnstile token is too long")
    val turnstileToken: String,
)