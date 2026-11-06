package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.domain.ask.ToolCall
import me.longng.finnish_learning_backend.service.ask.mcp.McpProfileClient
import me.longng.finnish_learning_backend.service.ask.mcp.McpTool
import org.springframework.stereotype.Component

/**
 * Runs the profile tools the model asked for in one reply (one round), for [ChatService] and
 * [FitCheckService]. The model never talks to the profile server itself.
 *
 * At most [AskProperties.Agent.maxReadsPerRound] requests run; any extra one gets an error result
 * ("Too many reads in one step…"), so the model learns the limit. Every request gets a result:
 * Bedrock refuses the next call if one is missing.
 */
@Component
class ProfileToolRunner(
    private val profileClient: McpProfileClient,
    properties: AskProperties,
) {
    private val maxReadsPerRound = properties.agent.maxReadsPerRound

    /**
     * Runs [requests], records each tool that ran in [turn]'s tool trace and reports it to
     * [listener]. Returns one result per request, in the same order.
     *
     * @param tools the tools the model was offered; a request for any other name still gets the
     *   server's error result, so the model can correct itself, but no step and no trace entry:
     *   it read nothing, and the visitor would see a tool that doesn't exist
     */
    fun runRound(
        turn: TurnInProgress,
        requests: List<ModelToolRequest>,
        tools: List<McpTool>,
        listener: AskProgressListener,
    ): List<ToolOutcome> {
        val tooMany = "Too many reads in one step; ask for at most $maxReadsPerRound."
        val toolNames = tools.mapTo(HashSet()) { it.name }
        val ran = requests.take(maxReadsPerRound).map { request -> run(turn, request, toolNames, listener) }
        val refused = requests.drop(maxReadsPerRound).map { ToolOutcome(it.toolUseId, tooMany, isError = true) }
        return ran + refused
    }

    private fun run(
        turn: TurnInProgress,
        request: ModelToolRequest,
        toolNames: Set<String>,
        listener: AskProgressListener,
    ): ToolOutcome {
        val known = request.name in toolNames
        if (known) listener.onStep(AskStep.ToolCalled(request.name, request.arguments))
        val result = profileClient.callTool(request.name, request.arguments)
        // Added at once, so a failure later in the turn still records the tools that ran.
        if (known) turn.toolTrace += ToolCall(tool = request.name, args = request.arguments, isError = result.isError)
        return ToolOutcome(request.toolUseId, result.text, result.isError)
    }
}