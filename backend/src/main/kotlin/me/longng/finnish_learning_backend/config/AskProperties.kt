package me.longng.finnish_learning_backend.config


import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import me.longng.finnish_learning_backend.service.ask.TurnstileClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Settings of the recruiter chatbot ("Ask"), bound from `app.ask.*` in `application.properties`.
 *
 * The values have no defaults here: `application.properties` sets every one of them, with an
 * environment-variable override, so each number is defined in one place.
 *
 * @property ipHashKey secret key of the IP hash (env `ASK_IP_HASH_KEY`). Never logged.
 *   Its length is checked by [me.longng.finnish_learning_backend.service.ask.IpHasher]
 */
@ConfigurationProperties("app.ask")
@Validated
data class AskProperties(
    val ipHashKey: String,
    @field:Valid val limits: Limits,
    @field:Valid val turnstile: Turnstile,
    @field:Valid val session: Session,
) {

    /**
     * Per-IP limits. Chat messages and fit checks count per Europe/Helsinki calendar day,
     * sessions per rolling hour.
     */
    data class Limits(
        @field:Min(1) val chatPerIpPerDay: Int,
        @field:Min(1) val fitChecksPerIpPerDay: Int,
        @field:Min(1) val sessionsPerIpPerHour: Int,
    )

    /**
     * Cloudflare Turnstile, the bot check a visitor passes before a chat session is created.
     *
     * @property secretKey the widget's secret key (env `TURNSTILE_SECRET_KEY`). Never logged. May
     *   be blank: the app still starts, and [TurnstileClient] refuses to create sessions.
     * @property verifyUrl Cloudflare's siteverify endpoint
     */
    data class Turnstile(
        val secretKey: String,
        @field:NotBlank val verifyUrl: String,
        @field:Min(1) val connectTimeoutMs: Long,
        @field:Min(1) val readTimeoutMs: Long,
    ) {
        /** Hides [secretKey], like [AskProperties.toString] hides the IP hash key. */
        override fun toString(): String =
            "Turnstile(secretKey=<hidden>, verifyUrl=$verifyUrl, " +
                    "connectTimeoutMs=$connectTimeoutMs, readTimeoutMs=$readTimeoutMs)"
    }

    /**
     * Chat sessions.
     *
     * @property ttlMinutes how long a session lasts after it is created
     * @property maxMessages chat messages allowed in one session
     */
    data class Session(
        @field:Min(1) val ttlMinutes: Long,
        @field:Min(1) val maxMessages: Int,
    )

    /** Hides [ipHashKey], so printing the settings (logs, debugger, error messages) never leaks it. */
    override fun toString(): String =
        "AskProperties(ipHashKey=<hidden>, limits=$limits, turnstile=$turnstile, session=$session)"
}