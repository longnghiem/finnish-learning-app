package me.longng.finnish_learning_backend.service.ask.mcp

/**
 * Reads the developer profile from the profile MCP server: the only way the chatbot learns
 * anything about the developer.
 *
 * The server is read-only and has two tools (`list_entries`, `get_entry`) and one resource
 * (the compact profile index). The model never talks to it directly: it asks for a tool, and the
 * chat service calls [callTool] for it.
 *
 * Every method throws [McpProfileUnavailableException] when the server can't be used (not
 * reachable, too slow, refusing the signature, or answering with a protocol error).
 */
interface McpProfileClient {

    /** The server's tools, for the model's tool list. */
    fun listTools(): List<McpTool>

    /**
     * Runs the tool [name] with [arguments], as the model asked.
     *
     * A mistake of the model (unknown tool, unknown entry id, invalid input) is not an exception:
     * it comes back as a result with [McpToolResult.isError] true, so the model can read it and
     * try again.
     */
    fun callTool(name: String, arguments: Map<String, Any?>): McpToolResult

    /** The compact profile index: one line per entry, `id | type | title | tags | summary`. */
    fun readIndex(): String
}

/**
 * One tool of the profile server.
 *
 * @property inputSchema the JSON Schema of the tool's input, as the server sent it
 */
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any?>,
)

/**
 * What a tool returned.
 *
 * @property isError true if the tool refused the call (e.g. unknown entry id); [text] then says
 *   why, for the model to read
 * @property text the tool's text output (an entry as JSON, or the error message)
 */
data class McpToolResult(
    val isError: Boolean,
    val text: String,
)

/**
 * Thrown when the profile server can't be used: not reachable, too slow, the signature refused
 * (403), a server error, or a protocol error (a bug on our side). The model can't fix any of
 * these, so the request ends with an error instead.
 */
class McpProfileUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)