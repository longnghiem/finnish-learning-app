package me.longng.finnish_learning_backend.controller.dto.ask

import com.fasterxml.jackson.annotation.JsonInclude
import me.longng.finnish_learning_backend.controller.AskController
import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.domain.ask.ToolCall
import me.longng.finnish_learning_backend.service.ask.AskBudgetExhaustedException
import me.longng.finnish_learning_backend.service.ask.AskConversationLimitException
import me.longng.finnish_learning_backend.service.ask.AskInputBlockedException
import me.longng.finnish_learning_backend.service.ask.AskModelUnavailableException
import me.longng.finnish_learning_backend.service.ask.AskRateLimitedException
import me.longng.finnish_learning_backend.service.ask.AskStep
import me.longng.finnish_learning_backend.service.ask.ChatAnswer
import me.longng.finnish_learning_backend.service.ask.FitCheckResult
import me.longng.finnish_learning_backend.service.ask.FitReport
import me.longng.finnish_learning_backend.service.ask.GuardrailUnavailableException
import me.longng.finnish_learning_backend.service.ask.mcp.McpProfileUnavailableException

/**
 * The `event:` names of the recruiter chatbot's SSE streams, as [AskController] sends them. The
 * frontend's `frontend/src/schemas/ask.ts` must match the event classes in this file.
 *
 * ```
 * event:step     data:{"kind":"GUARDRAIL"}
 * event:step     data:{"kind":"COMPOSING"}
 * event:step     data:{"kind":"TOOL_CALL","tool":"get_entry","args":{"id":"projects/x"}}
 * event:step     data:{"kind":"VERIFYING"}                              (fit check only)
 * event:answer   data:{"turnId":123,"text":"…","toolTrace":[{"tool":"get_entry","args":{…},"isError":false}]}
 * event:report   data:{"turnId":124,"report":{…},"toolTrace":[…]}      (fit check only)
 * event:error    data:{"code":"CAPPED","message":"…","capScope":"DAILY"}
 * event:done     data:{}
 * :keepalive                                                           (fit check only, every 15 s)
 * ```
 *
 * A chat stream ends with `answer` then `done`, a fit-check stream with `report` then `done`;
 * either may end with `error` then `done` instead. A stream that ends without `done` timed out or
 * broke; the frontend shows a generic "please try again". `:keepalive` is an SSE comment: it keeps
 * nginx from closing a quiet stream, and clients ignore it.
 */
object AskSseEventNames {
    const val STEP = "step"
    const val ANSWER = "answer"
    const val REPORT = "report"
    const val ERROR = "error"
    const val DONE = "done"
}

/**
 * The error codes the frontend reacts to, both in HTTP error bodies (before the stream starts) and
 * in `error` events (after it started).
 */
enum class AskErrorCode {
    /** HTTP 401: the session is unknown, expired or used from another network. */
    SESSION_EXPIRED,

    /** HTTP 403: Cloudflare rejected the bot-check token. */
    TURNSTILE_FAILED,

    /** HTTP 429 or stream: the visitor's IP reached a per-IP limit. */
    RATE_LIMITED,

    /** HTTP 429 or stream: the conversation reached its message limit. */
    CONVERSATION_LIMIT,

    /** HTTP 503: every worker is busy and the queue is full. */
    BUSY,

    /** Stream: the input guardrail refused the message. */
    BLOCKED,

    /** Stream: a spending cap is full; see [AskErrorEvent.capScope]. */
    CAPPED,

    /** HTTP 502 or stream: a service the chatbot depends on failed or was too slow. */
    UPSTREAM,

    /** Stream: anything else went wrong. */
    INTERNAL,
}

/**
 * A progress step: `event:step`. [tool] and [args] are set only for [Kind.TOOL_CALL], and left out
 * of the JSON otherwise.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AskStepEvent(
    val kind: Kind,
    val tool: String? = null,
    val args: Map<String, Any?>? = null,
) {
    /** What the backend is doing. */
    enum class Kind {
        /** The input guardrail let the message through. */
        GUARDRAIL,

        /** A profile tool runs, e.g. `get_entry`. */
        TOOL_CALL,

        /** The model is writing. In chat sent before every model call, so it can come more than once. */
        COMPOSING,

        /** Fit check: the report's quotes are being checked against the profile. */
        VERIFYING,
    }

    companion object {
        fun from(step: AskStep): AskStepEvent = when (step) {
            AskStep.GuardrailPassed -> AskStepEvent(Kind.GUARDRAIL)
            AskStep.Composing -> AskStepEvent(Kind.COMPOSING)
            AskStep.Verifying -> AskStepEvent(Kind.VERIFYING)
            is AskStep.ToolCalled -> AskStepEvent(Kind.TOOL_CALL, tool = step.tool, args = step.args)
        }
    }
}

/**
 * The answer: `event:answer`.
 *
 * @property turnId the stored turn, so a bug report can name it
 * @property toolTrace the profile tools that ran, shown as "How I found this"
 */
data class AskAnswerEvent(
    val turnId: Long,
    val text: String,
    val toolTrace: List<AskToolCall>,
) {
    companion object {
        fun from(answer: ChatAnswer) = AskAnswerEvent(
            turnId = answer.turnId,
            text = answer.text,
            toolTrace = answer.toolTrace.map { AskToolCall.from(it) },
        )
    }
}

/**
 * The fit report: `event:report`.
 *
 * @property turnId the stored turn, so a bug report can name it
 * @property report the verified report; every quote in it was found in the profile
 * @property toolTrace the profile tools that ran during research, shown as "How I found this"
 */
data class AskFitReportEvent(
    val turnId: Long,
    val report: FitReport,
    val toolTrace: List<AskToolCall>,
) {
    companion object {
        fun from(result: FitCheckResult) = AskFitReportEvent(
            turnId = result.turnId,
            report = result.report,
            toolTrace = result.toolTrace.map { AskToolCall.from(it) },
        )
    }
}

/**
 * One entry of [AskAnswerEvent.toolTrace] and [AskFitReportEvent.toolTrace].
 *
 * @property isError whether the tool answered with an error, e.g. an unknown entry id
 */
data class AskToolCall(
    val tool: String,
    val args: Map<String, Any?>,
    val isError: Boolean,
) {
    companion object {
        fun from(call: ToolCall) = AskToolCall(tool = call.tool, args = call.args, isError = call.isError)
    }
}

/**
 * Why the stream ended without an answer: `event:error`. [message] is written for the visitor;
 * it never contains details of the failure.
 *
 * @property capScope which cap is full, only for [AskErrorCode.CAPPED]
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AskErrorEvent(
    val code: AskErrorCode,
    val message: String,
    val capScope: AskBudgetExhaustedException.Scope? = null,
) {
    companion object {
        /**
         * The event for an exception thrown while answering.
         *
         * @param mode what was asked; only the text of a blocked input depends on it
         */
        fun from(ex: Exception, mode: AskMode = AskMode.CHAT): AskErrorEvent = when (ex) {
            is AskInputBlockedException -> AskErrorEvent(
                AskErrorCode.BLOCKED,
                when (mode) {
                    AskMode.CHAT -> "I can't answer that message. Please ask about Long's work, projects or skills."
                    AskMode.FIT_CHECK -> "I can't check this text. Please paste only the job description."
                },
            )
            is AskBudgetExhaustedException -> AskErrorEvent(
                AskErrorCode.CAPPED,
                when (ex.scope) {
                    AskBudgetExhaustedException.Scope.DAILY ->
                        "The chatbot has answered all it can for today. Please come back tomorrow."
                    AskBudgetExhaustedException.Scope.MONTHLY ->
                        "The chatbot has answered all it can for this month. Please come back next month."
                },
                capScope = ex.scope,
            )
            is AskConversationLimitException -> AskErrorEvent(
                AskErrorCode.CONVERSATION_LIMIT,
                "This conversation has reached its limit of ${ex.limit} messages. Please start a new one.",
            )
            is AskRateLimitedException -> AskErrorEvent(
                AskErrorCode.RATE_LIMITED,
                when (ex.kind) {
                    AskRateLimitedException.Kind.SESSIONS_PER_HOUR ->
                        "You have started ${ex.kind.describe(ex.limit)}. Please try again in an hour."
                    AskRateLimitedException.Kind.CHAT_PER_DAY, AskRateLimitedException.Kind.FIT_CHECKS_PER_DAY ->
                        "You have reached the limit of ${ex.kind.describe(ex.limit)}. Please try again tomorrow."
                },
            )
            is GuardrailUnavailableException, is McpProfileUnavailableException, is AskModelUnavailableException ->
                AskErrorEvent(AskErrorCode.UPSTREAM, "The assistant is unavailable right now. Please try again in a moment.")
            else -> AskErrorEvent(AskErrorCode.INTERNAL, "Something went wrong. Please try again.")
        }
    }
}