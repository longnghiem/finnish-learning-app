package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties

/**
 * One step of answering a request, reported while the request runs so the visitor sees progress
 * instead of a silent wait. The answer itself is sent whole at the end (no token streaming, see
 * ADR 0002).
 *
 * For a chat message the steps come in this order:
 * ```
 * GuardrailPassed
 * Composing                         before the first model call
 * ToolCalled, ToolCalled, …         the entries read in round 1
 * Composing                         before the next model call
 * …                                 more rounds
 * ```
 * There are at most [AskProperties.Agent.chatMaxToolRounds] rounds. A blocked message ends
 * before [GuardrailPassed]; a direct answer has no [ToolCalled] step.
 *
 * For a fit check ([FitCheckService]) the research calls only pick entries to read, so they send
 * no step of their own:
 * ```
 * GuardrailPassed
 * ToolCalled, ToolCalled, …         the entries read, over at most
 *                                   [AskProperties.Agent.fitMaxToolRounds] rounds
 * Composing                         before the one call that writes the report
 * Verifying                         while the report's quotes are checked against the profile
 * ```
 */
sealed interface AskStep {

    /** The input guardrail let the message through. */
    data object GuardrailPassed : AskStep

    /**
     * The model is reading what it has and writing: sent before every chat model call, and
     * before the fit check's report call.
     */
    data object Composing : AskStep

    /** The fit report's quotes are being checked against the profile ([QuoteVerifier]). */
    data object Verifying : AskStep

    /**
     * A profile tool is about to run, e.g. `get_entry` with `{"id": "projects/finnish-learning-app"}`.
     *
     * @property args the arguments exactly as the model sent them
     */
    data class ToolCalled(val tool: String, val args: Map<String, Any?>) : AskStep
}

/**
 * Receives the [AskStep]s of one request, on the thread that answers it.
 *
 * If [onStep] throws (e.g. the visitor closed the page and the stream is gone), the request stops
 * there and is recorded as failed: there is no point in paying for an answer nobody reads.
 */
fun interface AskProgressListener {
    fun onStep(step: AskStep)
}