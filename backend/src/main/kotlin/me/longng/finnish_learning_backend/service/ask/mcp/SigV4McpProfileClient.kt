package me.longng.finnish_learning_backend.service.ask.mcp

import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.client.transport.McpHttpClientTransportAuthorizationException
import io.modelcontextprotocol.spec.McpError
import io.modelcontextprotocol.spec.McpSchema
import me.longng.finnish_learning_backend.config.McpClientConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.ConnectException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException

/**
 * [McpProfileClient] over the MCP SDK's [McpSyncClient] (built by [McpClientConfig]), which signs
 * every request with [SigV4RequestSigner].
 *
 * - **Cached for 10 minutes:** the index and the tool list. Both change only when the profile
 *   server is deployed again, and the cache saves one or two Lambda calls per recruiter question.
 *   Tool results are never cached.
 * - **Tool errors vs. failures:** a tool error is the model's mistake and becomes a normal
 *   [McpToolResult]. A tool name that isn't in [listTools] is answered here, without a request.
 *   On success [McpSchema.CallToolResult.isError] is `null`, not false.
 *   Everything else (connection, 403, 5xx, timeout, a JSON-RPC protocol error) becomes
 *   [McpProfileUnavailableException].
 * - **Logging:** what failed, how long it took and the HTTP status or JSON-RPC code. Never tool
 *   arguments or results: they may contain what the recruiter typed.
 */
@Component
class SigV4McpProfileClient(
    private val mcpClient: McpSyncClient,
    private val clock: Clock = Clock.systemUTC(),
) : McpProfileClient {
    private val logger = LoggerFactory.getLogger(SigV4McpProfileClient::class.java)

    @Volatile
    private var cachedTools: Cached<List<McpTool>>? = null

    @Volatile
    private var cachedIndex: Cached<String>? = null

    override fun listTools(): List<McpTool> {
        cachedTools?.takeIf { it.isFresh() }?.let { return it.value }
        val tools = call("tools/list") { mcpClient.listTools() }.tools().map { tool ->
            McpTool(name = tool.name(), description = tool.description().orEmpty(), inputSchema = tool.inputSchema().orEmpty())
        }
        cachedTools = Cached(tools, clock.instant())
        return tools
    }

    override fun callTool(name: String, arguments: Map<String, Any?>): McpToolResult {
        require(name.isNotBlank()) { "Tool name must not be blank" }
        val toolNames = listTools().map { it.name }
        if (name !in toolNames) {
            // The model's mistake, not a broken server: answer it without a request. The name is
            // never logged, since only known names may reach the logs.
            return McpToolResult(isError = true, text = "Unknown tool \"$name\". Use one of: ${toolNames.joinToString(", ")}.")
        }
        val request = McpSchema.CallToolRequest.builder(name).arguments(arguments).build()
        val result = call("tools/call $name") { mcpClient.callTool(request) }
        val text = result.content().orEmpty()
            .filterIsInstance<McpSchema.TextContent>()
            .joinToString("\n") { it.text() }
        return McpToolResult(isError = result.isError() == true, text = truncated(name, text))
    }

    override fun readIndex(): String {
        cachedIndex?.takeIf { it.isFresh() }?.let { return it.value }
        val index = call("resources/read") { mcpClient.readResource(McpSchema.ReadResourceRequest.builder(PROFILE_INDEX_URI).build()) }
            .contents().orEmpty()
            .filterIsInstance<McpSchema.TextResourceContents>()
            .joinToString("\n") { it.text() }
        if (index.isBlank()) {
            // Without the index the model doesn't know which entries exist.
            throw McpProfileUnavailableException("The profile index is empty")
        }
        cachedIndex = Cached(index, clock.instant())
        return index
    }

    /** Runs [block] against the server, turning any failure into [McpProfileUnavailableException]. */
    private fun <T> call(operation: String, block: () -> T): T {
        val startNanos = System.nanoTime()
        try {
            return block()
        } catch (ex: RuntimeException) {
            val elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis()
            val reason = failureReason(ex)
            logger.warn("MCP {} failed after {} ms: {}", operation, elapsedMs, reason)
            throw McpProfileUnavailableException("MCP $operation failed: $reason", ex)
        }
    }

    /**
     * A short, log-safe description of a failure: the JSON-RPC code, the HTTP status or the kind
     * of error. Never the response body, which may echo the request.
     */
    private fun failureReason(ex: Throwable): String {
        val chain = generateSequence(ex) { it.cause }.take(MAX_CAUSES).toList()
        chain.firstNotNullOfOrNull { it as? McpError }?.let { return "JSON-RPC error ${it.jsonRpcError.code()}" }
        chain.firstNotNullOfOrNull { it as? McpHttpClientTransportAuthorizationException }
            ?.let { return "HTTP ${it.responseInfo.statusCode()}" }
        chain.firstNotNullOfOrNull { cause -> cause.message?.let { STATUS_CODE.find(it) } }
            ?.let { return "HTTP ${it.groupValues[1]}" }
        // For a 5xx the SDK only says "Failed to send message", without the status.
        if (chain.any { it.message.orEmpty().startsWith(SERVER_ERROR_MESSAGE) }) return "server error (HTTP 5xx)"
        if (chain.any { it is TimeoutException }) return "timeout"
        if (chain.any { it is ConnectException }) return "connection failed"
        return chain.last().javaClass.simpleName
    }

    /** Cuts [text] at [MAX_TOOL_RESULT_CHARS], so one tool result can't fill the model's input. */
    private fun truncated(toolName: String, text: String): String {
        if (text.length <= MAX_TOOL_RESULT_CHARS) return text
        logger.warn("MCP tool result truncated: tool={} chars={} kept={}", toolName, text.length, MAX_TOOL_RESULT_CHARS)
        return text.take(MAX_TOOL_RESULT_CHARS) + TRUNCATION_NOTE
    }

    private inner class Cached<T>(val value: T, private val storedAt: Instant) {
        fun isFresh(): Boolean = Duration.between(storedAt, clock.instant()) < CACHE_TTL
    }

    companion object {
        /** The address of the profile index resource on the profile server. */
        const val PROFILE_INDEX_URI = "profile://index"

        /**
         * The longest tool result passed on. A whole entry (a body of at most 6,000 characters plus
         * its other fields, as JSON) fits.
         */
        const val MAX_TOOL_RESULT_CHARS = 8_000

        /** Appended to a cut tool result, so the model knows something is missing. */
        const val TRUNCATION_NOTE = "\n[Result truncated.]"

        private val CACHE_TTL: Duration = Duration.ofMinutes(10)

        /** Matches the status in the SDK's transport errors, e.g. "Status code: 502" or "Status code:404". */
        private val STATUS_CODE = Regex("""Status code: ?(\d{3})""")

        private const val SERVER_ERROR_MESSAGE = "Failed to send message"

        private const val MAX_CAUSES = 10
    }
}