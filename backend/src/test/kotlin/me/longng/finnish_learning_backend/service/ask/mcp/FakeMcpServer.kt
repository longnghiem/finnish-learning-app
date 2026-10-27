package me.longng.finnish_learning_backend.service.ask.mcp

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * A tiny MCP server for tests, on a free local port. It answers the JSON-RPC methods the backend
 * uses with canned replies, like the real profile server: plain JSON (never a stream), no session,
 * and 405 for any method but POST.
 *
 * Tests change its behaviour through the `var`s and read what arrived in [received].
 */
class FakeMcpServer : AutoCloseable {

    /** One request as it arrived. Header names are in lower case. */
    data class Received(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray) {
        val bodyText: String get() = body.decodeToString()
    }

    /** A JSON-RPC answer: a result, or an error with a code. */
    sealed interface Reply {
        data class Result(val json: String) : Reply
        data class Error(val code: Int, val message: String) : Reply
    }

    private val mapper = JsonMapper.builder().build()
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

    val received = CopyOnWriteArrayList<Received>()

    /** The endpoint, like `ASK_MCP_URL`. */
    val url: String get() = "http://127.0.0.1:${server.address.port}/mcp"

    /** If set, every POST gets this HTTP status and an empty body. */
    @Volatile
    var forcedStatus: Int? = null

    /** If set, `initialize` gets this HTTP status (the other methods answer normally). */
    @Volatile
    var initializeStatus: Int? = null

    /** If set, each request waits this long before it is answered. */
    @Volatile
    var delayMs: Long = 0

    /** If set, checks each request; a non-null answer (the reason) gets 403, like AWS. */
    @Volatile
    var requestCheck: ((Received) -> String?)? = null

    @Volatile
    var toolsJson: String = DEFAULT_TOOLS_JSON

    @Volatile
    var indexText: String = DEFAULT_INDEX

    /** Answers `tools/call`: the tool name and its arguments in, a [Reply] out. */
    @Volatile
    var toolCall: (String, JsonNode) -> Reply = { name, arguments -> defaultToolCall(name, arguments) }

    init {
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/mcp") { exchange -> exchange.use { handle(it) } }
        server.start()
    }

    /** The JSON-RPC methods of the POSTs received, in order (e.g. `initialize`, `tools/list`). */
    fun rpcMethods(): List<String> = received.filter { it.method == "POST" }.map { mapper.readTree(it.body).path("method").asString() }

    fun count(rpcMethod: String): Int = rpcMethods().count { it == rpcMethod }

    private fun handle(exchange: HttpExchange) {
        val request = Received(
            method = exchange.requestMethod,
            path = exchange.requestURI.rawPath,
            headers = exchange.requestHeaders.entries.associate { (name, values) -> name.lowercase() to values.first() },
            body = exchange.requestBody.readAllBytes(),
        )
        received += request
        if (delayMs > 0) Thread.sleep(delayMs)

        requestCheck?.invoke(request)?.let { return exchange.send(403, """{"message":"Forbidden"}""") }
        if (request.method != "POST") {
            exchange.responseHeaders.add("Allow", "POST")
            return exchange.send(405, """{"jsonrpc":"2.0","error":{"code":-32000,"message":"Method not allowed."},"id":null}""")
        }
        forcedStatus?.let { return exchange.send(it, "") }

        val message = mapper.readTree(request.body)
        val rpcMethod = message.path("method").asString()
        if (!message.has("id")) {
            return exchange.send(202, "") // a notification, e.g. notifications/initialized
        }
        if (rpcMethod == "initialize") initializeStatus?.let { return exchange.send(it, "") }

        val reply = when (rpcMethod) {
            "initialize" -> Reply.Result(
                """{"protocolVersion":${message.path("params").path("protocolVersion")},""" +
                        """"capabilities":{"tools":{},"resources":{}},"serverInfo":{"name":"fake-profile","version":"0"}}""",
            )
            "tools/list" -> Reply.Result(toolsJson)
            "tools/call" -> toolCall(message.path("params").path("name").asString(), message.path("params").path("arguments"))
            "resources/read" -> Reply.Result(
                """{"contents":[{"uri":"profile://index","mimeType":"text/plain","text":${mapper.writeValueAsString(indexText)}}]}""",
            )
            else -> Reply.Error(-32601, "Method not found")
        }
        val id = message.path("id")
        val body = when (reply) {
            is Reply.Result -> """{"jsonrpc":"2.0","id":$id,"result":${reply.json}}"""
            is Reply.Error -> """{"jsonrpc":"2.0","id":$id,"error":{"code":${reply.code},"message":"${reply.message}"}}"""
        }
        exchange.send(200, body)
    }

    private fun HttpExchange.send(status: Int, body: String) {
        val bytes = body.toByteArray()
        if (bytes.isNotEmpty()) responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) responseBody.write(bytes)
    }

    private fun defaultToolCall(name: String, arguments: JsonNode): Reply = when (name) {
        "get_entry" -> when (val id = arguments.path("id").asString()) {
            "experience/acme-backend" -> textResult("""{"id":"$id","body":"Kafka pipeline"}""")
            else -> Reply.Result("""{"content":[{"type":"text","text":"Unknown entry id \"$id\"."}],"isError":true}""")
        }
        "list_entries" -> textResult("""[{"id":"experience/acme-backend"}]""")
        else -> Reply.Error(-32602, "Tool $name not found")
    }

    /** A successful result with one text block and, like the real server, no `isError` field. */
    fun textResult(text: String): Reply =
        Reply.Result("""{"content":[{"type":"text","text":${mapper.writeValueAsString(text)}}]}""")

    override fun close() {
        server.stop(0)
        (server.executor as java.util.concurrent.ExecutorService).shutdownNow()
    }

    companion object {
        const val DEFAULT_INDEX = "experience/acme-backend | experience | Backend developer, Acme Oy | kotlin, kafka | Built a Kafka pipeline."

        /** Like the real `tools/list` answer: the schemas carry a `$schema` line. */
        val DEFAULT_TOOLS_JSON = """
            {"tools":[
              {"name":"list_entries","description":"List the entries of the developer profile.",
               "inputSchema":{"${'$'}schema":"https://json-schema.org/draft/2020-12/schema","type":"object",
                 "properties":{"type":{"type":"string"}},"additionalProperties":false}},
              {"name":"get_entry","description":"Read one profile entry in full.",
               "inputSchema":{"${'$'}schema":"https://json-schema.org/draft/2020-12/schema","type":"object",
                 "properties":{"id":{"type":"string","maxLength":100}},"required":["id"],"additionalProperties":false}}
            ]}
        """.trimIndent()
    }
}