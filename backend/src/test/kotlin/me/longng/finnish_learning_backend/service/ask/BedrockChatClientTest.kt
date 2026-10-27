package me.longng.finnish_learning_backend.service.ask

import com.sun.net.httpserver.HttpServer
import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.config.testAskProperties
import me.longng.finnish_learning_backend.domain.ask.TokenUsage
import me.longng.finnish_learning_backend.service.ask.mcp.McpTool
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.document.Document
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse
import software.amazon.awssdk.services.bedrockruntime.model.ResourceNotFoundException
import software.amazon.awssdk.services.bedrockruntime.model.Message
import software.amazon.awssdk.services.bedrockruntime.model.StopReason
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultStatus
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage as BedrockTokenUsage

class BedrockChatClientTest {

    companion object {
        private const val SYSTEM = "You answer questions about Long."

        /** Like the real `get_entry` tool from the profile server, `$schema` line included. */
        val GET_ENTRY_TOOL = McpTool(
            name = "get_entry",
            description = "Read one profile entry in full.",
            inputSchema = mapOf(
                "\$schema" to "https://json-schema.org/draft/2020-12/schema",
                "type" to "object",
                "properties" to mapOf("id" to mapOf("type" to "string", "maxLength" to 100)),
                "required" to listOf("id"),
                "additionalProperties" to false,
            ),
        )

        /**
         * A real Converse answer (eu-north-1, 2026-10-17): three tool requests and no
         * text block at all.
         */
        private val REAL_TOOL_USE_ONLY_ANSWER = """
            {"output":{"message":{"role":"assistant","content":[
              {"toolUse":{"toolUseId":"tooluse_xHtKNkLXI6KUWpfaWWdZLM","name":"get_entry","input":{"id":"proj-quiz-pipeline"},"type":"tool_use"}},
              {"toolUse":{"toolUseId":"tooluse_nQfngJYXBgN30n77Eu7hDP","name":"get_entry","input":{"id":"story-kafka-read-model"},"type":"tool_use"}},
              {"toolUse":{"toolUseId":"tooluse_uiT71MjxSttbKNdgvMG6Lq","name":"get_entry","input":{"id":"story-oom-small-host"},"type":"tool_use"}}
            ]}},
            "stopReason":"tool_use",
            "usage":{"inputTokens":1768,"outputTokens":143,"totalTokens":1911,"cacheReadInputTokens":0,"cacheWriteInputTokens":0},
            "metrics":{"latencyMs":1189}}
        """.trimIndent()

        /** A real `usage` block (eu-north-1, 2026-10-18): most of the input was read from the cache. */
        private val REAL_CACHED_ANSWER = """
            {"output":{"message":{"role":"assistant","content":[{"text":"Yes."}]}},
            "stopReason":"end_turn",
            "usage":{"inputTokens":3,"outputTokens":26,"totalTokens":5804,"cacheReadInputTokens":5698,
                     "cacheWriteInputTokens":77,"cacheDetails":[{"ttl":"5m","inputTokens":77}]},
            "metrics":{"latencyMs":900}}
        """.trimIndent()

        fun textReply(text: String, usage: BedrockTokenUsage = usage(10, 5)): ConverseResponse =
            reply(StopReason.END_TURN, usage, ContentBlock.fromText(text))

        fun reply(stopReason: StopReason, usage: BedrockTokenUsage, vararg content: ContentBlock): ConverseResponse =
            ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder().role(ConversationRole.ASSISTANT).content(*content).build()))
                .stopReason(stopReason)
                .usage(usage)
                .build()

        fun toolUse(id: String, name: String, input: Map<String, Any?>): ContentBlock =
            ContentBlock.fromToolUse(ToolUseBlock.builder().toolUseId(id).name(name).input(input.toDocument()).build())

        fun usage(input: Int, output: Int, cacheRead: Int? = 0, cacheWrite: Int? = 0): BedrockTokenUsage =
            BedrockTokenUsage.builder()
                .inputTokens(input)
                .outputTokens(output)
                .totalTokens(input + output + (cacheRead ?: 0) + (cacheWrite ?: 0))
                .cacheReadInputTokens(cacheRead)
                .cacheWriteInputTokens(cacheWrite)
                .build()
    }

    private val bedrockRuntimeClient: BedrockRuntimeClient = mock()
    private val objectMapper = JsonMapper.builder().build()

    private fun client(model: AskProperties.Model = testAskProperties().model) =
        BedrockChatClient(bedrockRuntimeClient, objectMapper, testAskProperties(model = model))

    /** Calls [BedrockChatClient.converse] with a stubbed answer and returns the request it sent. */
    private fun sentRequest(
        client: BedrockChatClient = client(),
        messages: List<Message> = listOf(BedrockChatClient.userText("Has Long used Kafka?")),
        tools: List<McpTool> = listOf(GET_ENTRY_TOOL),
    ): ConverseRequest {
        whenever(bedrockRuntimeClient.converse(any<ConverseRequest>())).thenReturn(textReply("Yes."))
        client.converse(SYSTEM, messages, tools, maxTokens = 400)
        return argumentCaptor<ConverseRequest>().also { verify(bedrockRuntimeClient).converse(it.capture()) }.firstValue
    }

    @Test
    fun testConverse_PlacesOneCachePointAtEndOfLatestMessage() {
        val messages = listOf(
            BedrockChatClient.userText("Has Long used Kafka?"),
            BedrockChatClient.assistantText("Yes, in a read model."),
            BedrockChatClient.userText("Where?"),
        )

        val request = sentRequest(messages = messages)

        // One cache point in the whole request: at the end of the latest message.
        assertTrue(request.system().none { it.cachePoint() != null })
        val cachePoints = request.messages().flatMapIndexed { index, message ->
            message.content().mapIndexedNotNull { position, block -> (index to position).takeIf { block.cachePoint() != null } }
        }
        assertEquals(listOf(2 to 1), cachePoints)
        assertEquals("Where?", request.messages()[2].content()[0].text())
    }

    @Test
    fun testConverse_DoesNotChangeTheCallersMessages() {
        val messages = listOf(BedrockChatClient.userText("Has Long used Kafka?"))

        sentRequest(messages = messages)

        assertEquals(1, messages.single().content().size)
    }

    @Test
    fun testConverse_NoAdditionalFieldsByDefault() {
        val request = sentRequest()

        assertNull(request.additionalModelRequestFields())
        assertEquals("eu.anthropic.claude-haiku-4-5-20251001-v1:0", request.modelId())
    }

    @Test
    fun testConverse_SendsConfiguredAdditionalFields() {
        val model = testAskProperties().model.copy(additionalRequestFields = """{"thinking":{"type":"disabled"}}""")

        val request = sentRequest(client = client(model))

        val thinking = request.additionalModelRequestFields().asMap().getValue("thinking").asMap()
        assertEquals("disabled", thinking.getValue("type").asString())
    }

    @Test
    fun testConverse_InvalidAdditionalFieldsFailAtStartup() {
        val notJson = testAskProperties().model.copy(additionalRequestFields = "{thinking: off")
        val notAnObject = testAskProperties().model.copy(additionalRequestFields = """["thinking"]""")

        assertThrows<IllegalArgumentException> { client(notJson) }
        assertThrows<IllegalArgumentException> { client(notAnObject) }
    }

    @Test
    fun testConverse_SendsLimitsToolsAndTimeout() {
        val request = sentRequest()

        assertEquals(400, request.inferenceConfig().maxTokens())
        assertEquals(listOf("get_entry"), request.toolConfig().tools().map { it.toolSpec().name() })
        assertTrue(request.toolConfig().toolChoice().auto() != null)
        assertEquals(Duration.ofMillis(30000), request.overrideConfiguration().get().apiCallTimeout().get())
    }

    @Test
    fun testConverse_NoToolsNoToolConfig() {
        val request = sentRequest(tools = emptyList())

        assertNull(request.toolConfig())
    }

    @Test
    fun testToolSpec_StripsJsonSchemaKey() {
        val spec = BedrockChatClient.toolSpecification(GET_ENTRY_TOOL)

        val schema = spec.inputSchema().json().asMap()
        assertFalse(schema.containsKey("\$schema"))
        assertEquals(setOf("type", "properties", "required", "additionalProperties"), schema.keys)
        assertEquals(100, schema.getValue("properties").asMap().getValue("id").asMap().getValue("maxLength").asNumber().toInt())
        assertEquals("Read one profile entry in full.", spec.description())
    }

    @Test
    fun testToolSpec_BlankDescriptionLeftOut() {
        val spec = BedrockChatClient.toolSpecification(GET_ENTRY_TOOL.copy(description = ""))

        assertNull(spec.description())
    }

    @Test
    fun testExtractText_SkipsNonTextBlocks() {
        val content = listOf(
            toolUse("t1", "get_entry", mapOf("id" to "about")),
            ContentBlock.fromText("First part."),
            ContentBlock.fromText("  "),
            ContentBlock.fromText("Second part."),
        )

        assertEquals("First part.\n\nSecond part.", BedrockChatClient.extractText(content))
    }

    @Test
    fun testConverse_ReadsToolRequestsAsPlainValues() {
        whenever(bedrockRuntimeClient.converse(any<ConverseRequest>())).thenReturn(
            reply(StopReason.TOOL_USE, usage(10, 5), toolUse("t1", "list_entries", mapOf("type" to "project", "limit" to 3))),
        )

        val reply = client().converse(SYSTEM, listOf(BedrockChatClient.userText("Projects?")), listOf(GET_ENTRY_TOOL), 400)

        assertTrue(reply.wantsTools)
        val request = reply.toolRequests.single()
        assertEquals("t1", request.toolUseId)
        assertEquals("list_entries", request.name)
        // Whole numbers come back as Long, not as the SDK's own number type.
        assertEquals(mapOf("type" to "project", "limit" to 3L), request.arguments)
    }

    @Test
    fun testConverse_UsageWithMissingCacheCountsIsZero() {
        whenever(bedrockRuntimeClient.converse(any<ConverseRequest>()))
            .thenReturn(textReply("Yes.", usage(10, 5, cacheRead = null, cacheWrite = null)))

        val reply = client().converse(SYSTEM, listOf(BedrockChatClient.userText("Kafka?")), emptyList(), 400)

        assertEquals(TokenUsage(inputTokens = 10, outputTokens = 5, cacheReadTokens = 0, cacheWriteTokens = 0), reply.usage)
        assertFalse(reply.wantsTools)
    }

    @Test
    fun testConverse_AccessDeniedExplainsCause() {
        whenever(bedrockRuntimeClient.converse(any<ConverseRequest>()))
            .thenThrow(AccessDeniedException.builder().message("denied").build())

        val error = assertThrows<AskModelUnavailableException> {
            client().converse(SYSTEM, listOf(BedrockChatClient.userText("Kafka?")), emptyList(), 400)
        }

        assertTrue(error.message!!.contains("ASK_MODEL_ID"), error.message)
    }

    @Test
    fun testConverse_ModelNotFoundExplainsCause() {
        // What Bedrock answers for a wrong model id, and for an account without Anthropic's use case form.
        whenever(bedrockRuntimeClient.converse(any<ConverseRequest>()))
            .thenThrow(ResourceNotFoundException.builder().message("Model use case details have not been submitted").build())

        val error = assertThrows<AskModelUnavailableException> {
            client().converse(SYSTEM, listOf(BedrockChatClient.userText("Kafka?")), emptyList(), 400)
        }

        assertTrue(error.message!!.contains("ASK_MODEL_ID"), error.message)
        assertTrue(error.message!!.contains("use case form"), error.message)
    }

    @Test
    fun testConverse_SdkErrorBecomesUnavailable() {
        whenever(bedrockRuntimeClient.converse(any<ConverseRequest>()))
            .thenThrow(SdkClientException.create("connection reset"))

        assertThrows<AskModelUnavailableException> {
            client().converse(SYSTEM, listOf(BedrockChatClient.userText("Kafka?")), emptyList(), 400)
        }
    }

    @Test
    fun testToolResults_OneMessageInOrderWithNote() {
        val message = BedrockChatClient.toolResults(
            listOf(
                ToolOutcome("t1", "{\"id\":\"about\"}", isError = false),
                ToolOutcome("t2", "Unknown entry id \"nope\".", isError = true),
                ToolOutcome("t3", "", isError = false),
            ),
            note = "Tool limit reached.",
        )

        assertEquals(ConversationRole.USER, message.role())
        val results = message.content().mapNotNull { it.toolResult() }
        assertEquals(listOf("t1", "t2", "t3"), results.map { it.toolUseId() })
        assertEquals(listOf(ToolResultStatus.SUCCESS, ToolResultStatus.ERROR, ToolResultStatus.SUCCESS), results.map { it.status() })
        // Bedrock refuses empty text blocks, so an empty result is replaced.
        assertEquals("(empty result)", results[2].content().single().text())
        assertEquals("Tool limit reached.", message.content().last().text())
    }

    /** Runs [test] against a local HTTP server that answers every Converse call with [answer]. */
    private fun withFakeBedrock(answer: String, test: (BedrockChatClient, AtomicReference<String>) -> Unit) {
        val requestBody = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requestBody.set(exchange.requestBody.readAllBytes().decodeToString())
            val bytes = answer.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            BedrockRuntimeClient.builder()
                .region(Region.EU_NORTH_1)
                .endpointOverride(URI("http://127.0.0.1:${server.address.port}"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret")))
                .httpClient(UrlConnectionHttpClient.create())
                .build()
                .use { test(BedrockChatClient(it, objectMapper, testAskProperties()), requestBody) }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun testConverse_ToolUseOnlyReplyGivesEmptyText() {
        withFakeBedrock(REAL_TOOL_USE_ONLY_ANSWER) { client, _ ->
            val reply = client.converse(SYSTEM, listOf(BedrockChatClient.userText("Kafka?")), listOf(GET_ENTRY_TOOL), 400)

            assertEquals("", reply.text)
            assertEquals(StopReason.TOOL_USE, reply.stopReason)
            assertEquals(listOf("proj-quiz-pipeline", "story-kafka-read-model", "story-oom-small-host"), reply.toolRequests.map { it.arguments["id"] })
            assertEquals(TokenUsage(1768, 143, 0, 0), reply.usage)
        }
    }

    @Test
    fun testConverse_ReadsRealCacheUsage() {
        withFakeBedrock(REAL_CACHED_ANSWER) { client, _ ->
            val reply = client.converse(SYSTEM, listOf(BedrockChatClient.userText("Kafka?")), emptyList(), 400)

            assertEquals(TokenUsage(inputTokens = 3, outputTokens = 26, cacheReadTokens = 5698, cacheWriteTokens = 77), reply.usage)
            assertEquals("Yes.", reply.text)
        }
    }

    @Test
    fun testConverse_WireFormat() {
        withFakeBedrock(REAL_CACHED_ANSWER) { client, requestBody ->
            client.converse(SYSTEM, listOf(BedrockChatClient.userText("Kafka?")), listOf(GET_ENTRY_TOOL), 400)

            val sent = objectMapper.readTree(requestBody.get())
            // The JSON Bedrock receives: the cache point after the message text, none in system.
            assertEquals(listOf("text"), blockKinds(sent.path("system")))
            assertEquals(listOf("text", "cachePoint"), blockKinds(sent.path("messages")[0].path("content")))
            assertEquals("default", sent.path("messages")[0].path("content")[1].path("cachePoint").path("type").asString())
            val schema = sent.path("toolConfig").path("tools")[0].path("toolSpec").path("inputSchema").path("json")
            assertFalse(schema.has("\$schema"))
            assertTrue(sent.path("toolConfig").path("toolChoice").has("auto"))
            assertFalse(sent.has("additionalModelRequestFields"))
            assertEquals(400, sent.path("inferenceConfig").path("maxTokens").asInt())
        }
    }

    /** The kind of each content block in a JSON array, e.g. `["text", "cachePoint"]`. */
    private fun blockKinds(blocks: JsonNode): List<String> = (0 until blocks.size()).map { blocks[it].propertyNames().first() }

    @Test
    fun testDocumentValues_RoundTrip() {
        val value = mapOf("a" to listOf(1L, 2.5.toBigDecimal(), "x", true, null), "b" to mapOf("c" to false))

        assertEquals(value, value.toDocument().toPlainValue())
        assertEquals(Document.fromNull(), null.toDocument())
        assertThrows<IllegalArgumentException> { java.time.Instant.EPOCH.toDocument() }
    }
}