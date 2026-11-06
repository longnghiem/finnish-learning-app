package me.longng.finnish_learning_backend.config


import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import me.longng.finnish_learning_backend.domain.ask.DailyAskStats
import me.longng.finnish_learning_backend.service.ask.AskRetentionJob
import me.longng.finnish_learning_backend.service.ask.BedrockChatClient
import me.longng.finnish_learning_backend.service.ask.BedrockGuardrailService
import me.longng.finnish_learning_backend.service.ask.BudgetService
import me.longng.finnish_learning_backend.service.ask.ChatService
import me.longng.finnish_learning_backend.service.ask.CostCalculator
import me.longng.finnish_learning_backend.service.ask.FitCheckService
import me.longng.finnish_learning_backend.service.ask.TurnstileClient
import me.longng.finnish_learning_backend.service.ask.mcp.SigV4McpProfileClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
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
    @field:Valid val mcp: Mcp,
    @field:Valid val model: Model,
    @field:Valid val fitCheck: FitCheck,
    @field:Valid val retention: Retention,
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
     * @property maxMessageChars the longest chat message [ChatService] accepts
     */
    data class Session(
        @field:Min(1) val ttlMinutes: Long,
        @field:Min(1) val maxMessages: Int,
        @field:Min(1) val maxMessageChars: Int,
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

    /**
     * The connection to the profile MCP server, used by [SigV4McpProfileClient].
     *
     * @property url the server's MCP endpoint (env `ASK_MCP_URL`). In production: the Lambda function
     *   URL with `mcp` appended. Locally: `http://localhost:3001/mcp`, the server of `npm run dev`
     * @property region the AWS region of the Lambda (env `AWS_REGION`). It is part of every signature
     * @property signRequests whether to sign every request with AWS SigV4 (env `ASK_MCP_SIGN`). The
     *   function URL refuses unsigned requests with 403; only the local dev server, which has no
     *   auth, needs false
     * @property connectTimeoutMs the most opening a connection may take
     * @property requestTimeoutMs the most one MCP request may take, a Lambda cold start included
     */
    data class Mcp(
        @field:Pattern(regexp = "https?://.+", message = "must be an http:// or https:// URL") val url: String,
        @field:NotBlank val region: String,
        val signRequests: Boolean,
        @field:Min(1) val connectTimeoutMs: Long,
        @field:Min(1) val requestTimeoutMs: Long,
    )

    /**
     * The model that answers chat messages, called by [BedrockChatClient].
     *
     * @property id the Bedrock model or inference profile (env `ASK_MODEL_ID`). If it changes, change
     *   [Price] too, and check the model's prompt-caching minimum
     * @property additionalRequestFields JSON object sent as Converse `additionalModelRequestFields`
     *   (env `ASK_ADDITIONAL_MODEL_FIELDS`), or blank to send none, which is right for Haiku 4.5.
     *   Checked at startup by [BedrockChatClient]
     * @property timeoutMs the most one model call may take, the SDK's own retries included
     */
    data class Model(
        @field:NotBlank val id: String,
        val additionalRequestFields: String,
        @field:Min(1) val timeoutMs: Long,
    )

    /**
     * The fit check ([FitCheckService]): a job description checked against the profile.
     *
     * @property maxChars the longest job description accepted
     * @property reportTimeoutMs the most the report call may take. Longer than
     *   [Model.timeoutMs]: the report is the longest reply, and on its first use in 24 hours
     *   Bedrock first compiles the report's schema (≈ 22 s in total measured, of which ≈ 10 s
     *   compiling). The Bedrock client's read timeout (`app.bedrock.read-timeout-ms`, 120 s)
     *   caps it too
     */
    data class FitCheck(
        @field:Min(1) val maxChars: Int,
        @field:Min(1) val reportTimeoutMs: Long,
    )

    /**
     * How long conversations are kept, enforced by [AskRetentionJob]: sessions older than [days]
     * are deleted every night, with their turns. The daily totals ([DailyAskStats]) hold no personal
     * data and are kept.
     *
     * @property days how long a session and its turns are kept after the session was created
     * @property purgeCron when the purge runs: a Spring cron expression (second, minute, hour, day
     *   of month, month, day of week) in Europe/Helsinki time. [AskRetentionJob] reads the same key
     *   in its [Scheduled] annotation; it is here so that a missing value stops the app at startup
     */
    data class Retention(
        @field:Min(1) val days: Int,
        @field:NotBlank val purgeCron: String,
    )

    /** Hides [ipHashKey], so printing the settings (logs, debugger, error messages) never leaks it. */
    override fun toString(): String =
        "AskProperties(ipHashKey=<hidden>, limits=$limits, turnstile=$turnstile, " +
                "session=$session, guardrail=$guardrail, agent=$agent, budget=$budget, price=$price, " +
                "mcp=$mcp, model=$model, fitCheck=$fitCheck, retention=$retention)"
}