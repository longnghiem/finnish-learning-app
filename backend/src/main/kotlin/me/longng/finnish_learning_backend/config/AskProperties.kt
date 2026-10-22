package me.longng.finnish_learning_backend.config


import jakarta.validation.Valid
import jakarta.validation.constraints.Min
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

    /** Hides [ipHashKey], so printing the settings (logs, debugger, error messages) never leaks it. */
    override fun toString(): String = "AskProperties(ipHashKey=<hidden>, limits=$limits)"
}