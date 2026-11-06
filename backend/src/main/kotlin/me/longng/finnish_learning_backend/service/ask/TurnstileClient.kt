package me.longng.finnish_learning_backend.service.ask

import com.fasterxml.jackson.annotation.JsonProperty
import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.config.TurnstileConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.InetAddress

/**
 * Checks a Cloudflare Turnstile token with Cloudflare's siteverify API.
 *
 * The browser shows the Turnstile widget; when the visitor passes it, the widget gives the browser
 * a token, which the browser sends to the backend. Only the backend can check it, because the check
 * needs the secret key. A token is valid for 300 seconds and can be checked only once.
 *
 * A failed check has two very different causes, told apart by Cloudflare's error codes:
 * - the **visitor's** token is bad (invalid, expired, already used): [verify] returns false, and
 *   the visitor can solve the widget again;
 * - **our** side is broken (wrong or missing secret key, malformed request, Cloudflare error):
 *   [verify] throws [TurnstileVerificationException]. Every visitor would fail, so it is reported
 *   as a server error instead of blaming them.
 *
 * There is no retry: a failed call already costs the visitor up to the connect + read timeout
 * (see [TurnstileConfig]), and they can simply try again.
 *
 * The token and the secret key are never logged.
 */
@Component
class TurnstileClient(
    @Qualifier("turnstileRestClient") private val restClient: RestClient,
    properties: AskProperties,
) {
    private val logger = LoggerFactory.getLogger(TurnstileClient::class.java)

    private val settings = properties.turnstile

    init {
        if (settings.secretKey.isBlank()) {
            logger.warn(
                "app.ask.turnstile.secret-key (env TURNSTILE_SECRET_KEY) is not set: " +
                        "the recruiter chatbot can't create sessions",
            )
        }
    }

    /**
     * Asks Cloudflare whether [token] is a valid, unused Turnstile token.
     *
     * @param remoteIp the visitor's address. Cloudflare uses it to spot a token that was solved
     *   somewhere else.
     * @return true if the token is valid; false if Cloudflare rejected it (invalid, expired or
     *   already used)
     * @throws IllegalArgumentException if [token] is blank or longer than [MAX_TOKEN_LENGTH]
     * @throws TurnstileVerificationException if the check couldn't be made: no secret key,
     *   Cloudflare unreachable, too slow or failing, or Cloudflare reporting a problem on our side
     */
    fun verify(token: String, remoteIp: InetAddress): Boolean {
        require(token.isNotBlank()) { "Turnstile token must not be blank" }
        require(token.length <= MAX_TOKEN_LENGTH) {
            "Turnstile token must be at most $MAX_TOKEN_LENGTH characters, got ${token.length}"
        }
        if (settings.secretKey.isBlank()) {
            throw TurnstileVerificationException("Turnstile secret key is not configured (env TURNSTILE_SECRET_KEY)")
        }

        val form = LinkedMultiValueMap<String, String>().apply {
            add("secret", settings.secretKey)
            add("response", token)
            add("remoteip", remoteIp.hostAddress)
        }
        val response = try {
            restClient.post()
                .uri(settings.verifyUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                // A 4xx still carries the error codes in its body, so it is read like a 200.
                .onStatus(HttpStatusCode::is4xxClientError) { _, _ -> }
                .body(SiteverifyResponse::class.java)
        } catch (ex: RestClientException) {
            throw TurnstileVerificationException("Turnstile siteverify call failed", ex)
        } ?: throw TurnstileVerificationException("Turnstile siteverify returned an empty body")

        if (response.success) {
            return true
        }
        val ourProblems = response.errorCodes.filter { it in SERVER_SIDE_ERRORS }
        if (ourProblems.isNotEmpty()) {
            throw TurnstileVerificationException("Turnstile siteverify reported a server-side problem: $ourProblems")
        }
        logger.warn("Turnstile rejected a token: errorCodes={}", response.errorCodes)
        return false
    }

    /** The part of Cloudflare's siteverify response that is used. */
    private data class SiteverifyResponse(
        val success: Boolean = false,
        @JsonProperty("error-codes") val errorCodes: List<String> = emptyList(),
    )

    companion object {
        /** Longest token Cloudflare issues. */
        const val MAX_TOKEN_LENGTH = 2048

        /** Error codes that mean our request or Cloudflare is at fault, not the visitor's token. */
        private val SERVER_SIDE_ERRORS = setOf(
            "missing-input-secret",
            "invalid-input-secret",
            "bad-request",
            "internal-error",
        )
    }
}

/**
 * Thrown when a Turnstile token couldn't be checked at all (see [TurnstileClient.verify]).
 * A server-side problem, mapped to HTTP 502: the visitor did nothing wrong.
 */
class TurnstileVerificationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)