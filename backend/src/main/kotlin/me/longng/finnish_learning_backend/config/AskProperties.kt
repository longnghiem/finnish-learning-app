package me.longng.finnish_learning_backend.config


import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import me.longng.finnish_learning_backend.service.ask.BedrockGuardrailService
import me.longng.finnish_learning_backend.service.ask.BudgetService
import me.longng.finnish_learning_backend.service.ask.CostCalculator
import me.longng.finnish_learning_backend.service.ask.TurnstileClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.math.BigDecimal

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
    @field:Valid val guardrail: Guardrail,
    @field:Valid val agent: Agent,
    @field:Valid val budget: Budget,
    @field:Valid val price: Price,
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

    /**
     * The input guardrail, see [BedrockGuardrailService]. Each check returns a score per category,
     * in steps of 0.2 from 0.0 to 1.0. A score at or above its threshold blocks the input.
     *
     * The thresholds must be above 0.0 (at 0.0 every input would be blocked) and at most 1.0.
     *
     * @property contentThreshold for the content filter (violence, hate, sexual, misconduct, insults)
     * @property chatPromptAttackThreshold for prompt attacks in chat messages
     * @property fitPromptAttackThreshold for prompt attacks in job descriptions. Higher than the chat
     *   threshold, because a job description is naturally full of instructions ("You will…")
     * @property timeoutMs the most one check may take, including the connection
     */
    data class Guardrail(
        @field:DecimalMin(value = "0.0", inclusive = false) @field:DecimalMax("1.0")
        val contentThreshold: Double,
        @field:DecimalMin(value = "0.0", inclusive = false) @field:DecimalMax("1.0")
        val chatPromptAttackThreshold: Double,
        @field:DecimalMin(value = "0.0", inclusive = false) @field:DecimalMax("1.0")
        val fitPromptAttackThreshold: Double,
        @field:Min(1) val timeoutMs: Long,
    )

    /**
     * Limits on how the chatbot answers one request (a chat message or a fit check).
     *
     * The model doesn't answer in one go. It may first ask to read profile entries; the backend
     * reads them and calls the model again with what it read. One such ask-and-read step is a
     * **round**. These limits stop the loop and cap how long each reply can be, so one request
     * can't cost an unlimited amount. [CostCalculator.estimateMax] uses them to work out the most
     * a request can cost.
     *
     * A fit check's research calls only pick what to read next, so they get a short reply limit.
     * Only its last call, which writes the report, gets the long one.
     *
     * @property maxReadsPerRound the most entries the model may read in one round, in both modes.
     *   Any extra read requests get an error back instead
     * @property chatMaxTokens the longest reply of every chat call. All chat calls get the same
     *   limit, because any of them may turn out to be the answer
     * @property chatMaxToolRounds the most rounds per chat message. After that, the model must answer
     * @property fitStepMaxTokens the longest reply of each fit-check research call
     * @property fitReportMaxTokens the longest reply of the fit-check report
     * @property fitMaxToolRounds the most research rounds per fit check. After that, the model must
     *   write the report
     */
    data class Agent(
        @field:Min(1) val maxReadsPerRound: Int,
        @field:Min(1) val chatMaxTokens: Int,
        @field:Min(1) val chatMaxToolRounds: Int,
        @field:Min(1) val fitStepMaxTokens: Int,
        @field:Min(1) val fitReportMaxTokens: Int,
        @field:Min(1) val fitMaxToolRounds: Int,
    )

    /**
     * The spending caps, enforced by [BudgetService]. One pool for chat and fit checks, counted
     * per Europe/Helsinki calendar day and month.
     *
     * @property monthlyUsd the hard ceiling for a calendar month
     * @property dailyUsd the most one day may use, so that a busy day can't use up the month
     * @property toolResultTokens the input allowed for each tool result in the worst-case
     *   estimate of [CostCalculator.estimateMax]
     */
    data class Budget(
        @field:DecimalMin(value = "0.0", inclusive = false) val monthlyUsd: BigDecimal,
        @field:DecimalMin(value = "0.0", inclusive = false) val dailyUsd: BigDecimal,
        @field:Min(1) val toolResultTokens: Int,
    )

    /**
     * Prices in USD, used by [CostCalculator]. Model prices are per million tokens, guardrail
     * prices per 1,000 text units.
     *
     * @property inputPerMillionTokens uncached input tokens
     * @property outputPerMillionTokens output tokens, written by the model
     * @property cacheReadPerMillionTokens input tokens read from the prompt cache
     * @property cacheWritePerMillionTokens input tokens written to the prompt cache
     * @property guardrailContentPerKUnits the guardrail's content filter
     * @property guardrailPromptAttackPerKUnits the guardrail's prompt-attack check
     */
    data class Price(
        @field:DecimalMin(value = "0.0", inclusive = false) val inputPerMillionTokens: BigDecimal,
        @field:DecimalMin(value = "0.0", inclusive = false) val outputPerMillionTokens: BigDecimal,
        @field:DecimalMin(value = "0.0", inclusive = false) val cacheReadPerMillionTokens: BigDecimal,
        @field:DecimalMin(value = "0.0", inclusive = false) val cacheWritePerMillionTokens: BigDecimal,
        @field:DecimalMin(value = "0.0", inclusive = false) val guardrailContentPerKUnits: BigDecimal,
        @field:DecimalMin(value = "0.0", inclusive = false) val guardrailPromptAttackPerKUnits: BigDecimal,
    )

    /** Hides [ipHashKey], so printing the settings (logs, debugger, error messages) never leaks it. */
    override fun toString(): String =
        "AskProperties(ipHashKey=<hidden>, limits=$limits, turnstile=$turnstile, " +
                "session=$session, guardrail=$guardrail, agent=$agent, budget=$budget, price=$price)"
}