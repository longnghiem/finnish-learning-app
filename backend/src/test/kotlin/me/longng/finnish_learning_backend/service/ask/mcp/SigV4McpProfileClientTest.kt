package me.longng.finnish_learning_backend.service.ask.mcp

import io.modelcontextprotocol.client.McpSyncClient
import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.config.McpClientConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SigV4McpProfileClientTest {

    /** A [Clock] the tests can move forward. */
    private class MutableClock(@Volatile var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = fixed(current, zone)
        override fun instant(): Instant = current
    }

    private val server = FakeMcpServer()
    private val clock = MutableClock(Instant.parse("2098-10-15T12:00:00Z"))
    private val mcpClients = mutableListOf<McpSyncClient>()

    private fun settings(url: String = server.url) = AskProperties.Mcp(
        url = url,
        region = "eu-north-1",
        signRequests = false,
        connectTimeoutMs = 1000,
        requestTimeoutMs = 2000,
    )

    /** A client set up exactly like the app's ([McpClientConfig.createMcpClient]). */
    private fun client(url: String = server.url, signer: SigV4RequestSigner? = null): SigV4McpProfileClient {
        val mcpClient = McpClientConfig.createMcpClient(settings(url), signer).also { mcpClients += it }
        return SigV4McpProfileClient(mcpClient, clock)
    }

    /** Waits up to 2 s for [condition]: the SDK sends its GET on a background thread. */
    private fun eventually(condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos()
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue(condition(), "condition not met within 2 s")
    }

    @AfterEach
    fun closeAll() {
        mcpClients.forEach { it.close() }
        server.close()
    }

    @Test
    fun testListTools_MapsSpecs() {
        val tools = client().listTools()

        assertEquals(listOf("list_entries", "get_entry"), tools.map { it.name })
        val getEntry = tools.single { it.name == "get_entry" }
        assertEquals("Read one profile entry in full.", getEntry.description)
        assertEquals("object", getEntry.inputSchema["type"])
        assertEquals(listOf("id"), getEntry.inputSchema["required"])
        // Passed on unchanged; whoever builds the model's tool list decides what to drop.
        assertTrue(getEntry.inputSchema.containsKey("\$schema"))
    }

    @Test
    fun testListTools_Cached() {
        val client = client()

        client.listTools()
        client.listTools()

        assertEquals(1, server.count("tools/list"))
    }

    @Test
    fun testCallTool_Success() {
        val result = client().callTool("get_entry", mapOf("id" to "experience/acme-backend"))

        assertFalse(result.isError)
        assertEquals("""{"id":"experience/acme-backend","body":"Kafka pipeline"}""", result.text)
    }

    @Test
    fun testCallTool_NullIsErrorMeansSuccess() {
        // The real server leaves out "isError" on success, so the SDK's isError() is null.
        server.toolCall = { _, _ -> server.textResult("ok") }

        val result = client().callTool("list_entries", emptyMap())

        assertFalse(result.isError)
        assertEquals("ok", result.text)
    }

    @Test
    fun testCallTool_ErrorResult() {
        val result = client().callTool("get_entry", mapOf("id" to "experience/nope"))

        assertTrue(result.isError)
        assertEquals("Unknown entry id \"experience/nope\".", result.text)
    }

    @Test
    fun testCallTool_UnknownToolIsErrorResult() {
        val result = client().callTool("search_entries", emptyMap())

        assertTrue(result.isError)
        assertEquals("Unknown tool \"search_entries\". Use one of: list_entries, get_entry.", result.text)
        assertEquals(0, server.count("tools/call"))
    }

    @Test
    fun testCallTool_ProtocolErrorBecomesUnavailable() {
        server.toolCall = { _, _ -> FakeMcpServer.Reply.Error(-32602, "Invalid params") }

        val error = assertThrows<McpProfileUnavailableException> { client().callTool("list_entries", emptyMap()) }

        assertTrue(error.message!!.contains("JSON-RPC error -32602"), error.message)
    }

    @Test
    fun testCallTool_ArgumentsSentAsUtf8() {
        client().callTool("get_entry", mapOf("id" to "Hyvää päivää ✓"))

        val call = server.received.single { it.bodyText.contains("tools/call") }
        assertTrue(call.bodyText.contains("Hyvää päivää ✓"), call.bodyText)
    }

    @Test
    fun testCallTool_LongResultTruncated() {
        server.toolCall = { _, _ -> server.textResult("x".repeat(SigV4McpProfileClient.MAX_TOOL_RESULT_CHARS + 500)) }

        val result = client().callTool("list_entries", emptyMap())

        assertEquals(
            "x".repeat(SigV4McpProfileClient.MAX_TOOL_RESULT_CHARS) + SigV4McpProfileClient.TRUNCATION_NOTE,
            result.text,
        )
    }

    @Test
    fun testCallTool_NeverCached() {
        val client = client()

        client.callTool("list_entries", emptyMap())
        client.callTool("list_entries", emptyMap())

        assertEquals(2, server.count("tools/call"))
        // The tool-name check uses the cached tool list.
        assertEquals(1, server.count("tools/list"))
    }

    @Test
    fun testReadIndex_Cached() {
        val client = client()

        assertEquals(FakeMcpServer.DEFAULT_INDEX, client.readIndex())
        assertEquals(FakeMcpServer.DEFAULT_INDEX, client.readIndex())

        assertEquals(1, server.count("resources/read"))
    }

    @Test
    fun testReadIndex_RefreshedAfterTenMinutes() {
        val client = client()
        client.readIndex()
        server.indexText = "projects/new | project | New project | kotlin | Added after a deploy."

        clock.current = clock.current.plus(Duration.ofMinutes(10))

        assertEquals("projects/new | project | New project | kotlin | Added after a deploy.", client.readIndex())
        assertEquals(2, server.count("resources/read"))
    }

    @Test
    fun testReadIndex_EmptyIndexUnavailable() {
        server.indexText = ""

        assertThrows<McpProfileUnavailableException> { client().readIndex() }
    }

    @Test
    fun testCall_ServerDown() {
        // A port that was free a moment ago: nothing listens there.
        val freePort = java.net.ServerSocket().use { socket ->
            socket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
            socket.localPort
        }

        val error = assertThrows<McpProfileUnavailableException> { client(url = "http://127.0.0.1:$freePort/mcp").listTools() }

        assertTrue(error.message!!.contains("connection failed"), error.message)
    }

    @Test
    fun testCall_Forbidden() {
        server.forcedStatus = 403

        val error = assertThrows<McpProfileUnavailableException> { client().listTools() }

        assertTrue(error.message!!.contains("HTTP 403"), error.message)
    }

    @Test
    fun testCall_ServerError() {
        server.forcedStatus = 502

        val error = assertThrows<McpProfileUnavailableException> { client().listTools() }

        assertTrue(error.message!!.contains("server error"), error.message)
    }

    @Test
    fun testCall_Timeout() {
        server.delayMs = 3000 // longer than the 2 s request timeout

        val error = assertThrows<McpProfileUnavailableException> { client().listTools() }

        assertTrue(error.message!!.contains("timeout"), error.message)
    }

    @Test
    fun testConnect_GetAnswered405IsTolerated() {
        val client = client()

        client.listTools()
        eventually { server.received.any { it.method == "GET" } }
        client.callTool("list_entries", emptyMap())

        assertEquals(listOf("initialize", "notifications/initialized", "tools/list", "tools/call"), server.rpcMethods())
        assertEquals(1, server.received.count { it.method == "GET" })
    }

    @Test
    fun testConnect_HandshakeOnlyOnce() {
        val client = client()

        client.listTools()
        client.readIndex()
        client.callTool("list_entries", emptyMap())

        assertEquals(1, server.count("initialize"))
    }

    @Test
    fun testConnect_RetriedAfterFailedHandshake() {
        val client = client()
        server.initializeStatus = 503
        assertThrows<McpProfileUnavailableException> { client.listTools() }

        server.initializeStatus = null

        assertEquals(2, client.listTools().size)
        assertEquals(2, server.count("initialize"))
    }

    @Test
    fun testCalls_SignedRequestsAcceptedByVerifyingServer() {
        val verifier = SigV4TestVerifier(SECRET_KEY)
        val failures = mutableListOf<String>()
        server.requestCheck = { request ->
            verifier.verify(request.method, request.path, request.headers, request.body)?.also { failures += "${request.method}: $it" }
        }
        val client = client(signer = signer(SECRET_KEY))

        client.listTools()
        client.callTool("get_entry", mapOf("id" to "Hyvää päivää ✓"))
        client.readIndex()
        eventually { server.received.any { it.method == "GET" } }

        assertEquals(emptyList(), failures)
        assertTrue(server.received.all { it.headers["x-amz-security-token"] == SESSION_TOKEN })
    }

    @Test
    fun testCalls_WrongSecretRefused() {
        val verifier = SigV4TestVerifier(SECRET_KEY)
        server.requestCheck = { request -> verifier.verify(request.method, request.path, request.headers, request.body) }

        val error = assertThrows<McpProfileUnavailableException> { client(signer = signer("some-other-secret")).listTools() }

        assertTrue(error.message!!.contains("HTTP 403"), error.message)
    }

    private fun signer(secretKey: String) = SigV4RequestSigner(
        StaticCredentialsProvider.create(AwsSessionCredentials.create(ACCESS_KEY, secretKey, SESSION_TOKEN)),
        "eu-north-1",
    )

    private companion object {
        // Made up for the tests: not real AWS credentials.
        const val ACCESS_KEY = "AKIDTESTONLY00000000"
        const val SECRET_KEY = "test-only-secret-not-a-real-key"
        const val SESSION_TOKEN = "test-only-session-token"
    }
}