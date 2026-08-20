package me.longng.finnish_learning_backend.controller

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.controller.dto.EssayCefrLevel
import me.longng.finnish_learning_backend.controller.dto.EssayIssue
import me.longng.finnish_learning_backend.controller.dto.EssayIssueKind
import me.longng.finnish_learning_backend.controller.dto.EvaluateEssayRequest
import me.longng.finnish_learning_backend.controller.dto.EvaluateEssayResponse
import me.longng.finnish_learning_backend.domain.Role
import me.longng.finnish_learning_backend.persistence.EssayTopicRepository
import me.longng.finnish_learning_backend.persistence.TopicRepository
import me.longng.finnish_learning_backend.persistence.UserRepository
import me.longng.finnish_learning_backend.service.JwtService
import me.longng.finnish_learning_backend.service.bedrock.BedrockEssayClient
import me.longng.finnish_learning_backend.service.bedrock.EssayEvaluationMisconfiguredException
import me.longng.finnish_learning_backend.service.bedrock.EssayEvaluationUpstreamException
import me.longng.finnish_learning_backend.service.bedrock.EssayQuotaTracker
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@Transactional
class EssayEvaluationControllerTest {

    @MockitoBean
    private lateinit var bedrockEssayClient: BedrockEssayClient

    @MockitoBean
    private lateinit var quotaTracker: EssayQuotaTracker

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var topicRepository: TopicRepository

    @Autowired
    private lateinit var essayTopicRepository: EssayTopicRepository

    private lateinit var token: String
    private var topicId: Int = 0
    private var promptId: Int = 0

    @BeforeEach
    fun setup() {
        val user = userRepository.insert(
            username = "essayuser",
            passwordHash = passwordEncoder.encode("password123")!!,
            role = Role.USER,
        )
        token = jwtService.generateToken(user.id, user.username, user.role)

        topicId = checkNotNull(topicRepository.findAll().firstOrNull { it.name == "Arkielämä" }?.id)
        promptId = essayTopicRepository.findByTopicId(topicId).first().id

        // Quota available unless a test says otherwise.
        whenever(quotaTracker.tryConsume(any(), any())).thenReturn(true)
    }

    private fun evaluateBody(essay: String = VALID_ESSAY, prompt: Int = promptId): String =
        objectMapper.writeValueAsString(EvaluateEssayRequest(promptId = prompt, essay = essay))

    @Test
    fun testGetPrompts_ReturnsSeededPrompts() {
        mockMvc.get("/api/essays/prompts?topicId=$topicId") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(greaterThanOrEqualTo(3)) }
            jsonPath("$[0].id") { isNumber() }
            jsonPath("$[0].title") { isNotEmpty() }
        }
    }

    @Test
    fun testGetPrompts_UnknownTopic() {
        mockMvc.get("/api/essays/prompts?topicId=99999") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun testGetPrompts_Unauthenticated() {
        // Proves default-deny covers /api/essays/** with no rule in WebSecurityConfig.
        mockMvc.get("/api/essays/prompts?topicId=$topicId").andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun testEvaluate_Success() {
        whenever(bedrockEssayClient.evaluate(any(), any())).thenReturn(RESPONSE)

        mockMvc.post("/api/essays/evaluate") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = evaluateBody()
        }.andExpect {
            status { isOk() }
            jsonPath("$.cefrLevel") { value("B1.1") }
            jsonPath("$.onTopic") { value(true) }
            jsonPath("$.issues.length()") { value(2) }
            jsonPath("$.issues[0].kind") { value("GRAMMAR") }
            jsonPath("$.issues[0].original") { value("minä menen kauppaan eilen") }
            jsonPath("$.issues[0].suggestion") { value("minä menin kauppaan eilen") }
            jsonPath("$.issues[1].kind") { value("TYPO") }
            jsonPath("$.feedback") { value("Good structure. Watch your past tense.") }
        }
    }

    @Test
    fun testEvaluate_Unauthenticated() {
        mockMvc.post("/api/essays/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = evaluateBody()
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun testEvaluate_TooShort() {
        mockMvc.post("/api/essays/evaluate") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = evaluateBody(essay = "a".repeat(299))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun testEvaluate_UnknownPrompt() {
        mockMvc.post("/api/essays/evaluate") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = evaluateBody(prompt = 99999)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun testEvaluate_QuotaExceeded() {
        whenever(quotaTracker.tryConsume(any(), any())).thenReturn(false)

        mockMvc.post("/api/essays/evaluate") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = evaluateBody()
        }.andExpect {
            status { isTooManyRequests() }
            jsonPath("$.message") { value(containsString("Try again tomorrow")) }
        }
    }

    @Test
    fun testEvaluate_UpstreamFailure() {
        whenever(bedrockEssayClient.evaluate(any(), any())).thenThrow(
            EssayEvaluationUpstreamException(
                "Bedrock upstream error: model eu.anthropic.claude-haiku-4-5-20251001-v1:0 " +
                        "threw arn:aws:bedrock:eu-north-1:123456789012:inference-profile/x",
            ),
        )

        mockMvc.post("/api/essays/evaluate") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = evaluateBody()
        }.andExpect {
            status { isBadGateway() }
            jsonPath("$.message") {
                value("The essay evaluation service is currently unavailable. Please try again later.")
            }
            // The exception text names the model and an account ARN. None of it may reach the client.
            content { string(not(containsString("arn:aws:bedrock"))) }
            content { string(not(containsString("eu.anthropic"))) }
        }
    }

    @Test
    fun testEvaluate_Misconfigured() {
        whenever(bedrockEssayClient.evaluate(any(), any())).thenThrow(
            EssayEvaluationMisconfiguredException(
                "Bedrock denied access to model 'eu.anthropic.claude-haiku-4-5-20251001-v1:0'. " +
                        "The IAM role is missing bedrock:InvokeModel.",
            ),
        )

        mockMvc.post("/api/essays/evaluate") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = evaluateBody()
        }.andExpect {
            status { isBadGateway() }
            jsonPath("$.message") { value("Essay evaluation is not configured on this server.") }
            content { string(not(containsString("bedrock:InvokeModel"))) }
        }
    }


    companion object {
        private val VALID_ESSAY = "Minä menin eilen kahvilaan. ".repeat(15)

        private val RESPONSE = EvaluateEssayResponse(
            cefrLevel = EssayCefrLevel.B1_1,
            onTopic = true,
            issues = listOf(
                EssayIssue(EssayIssueKind.GRAMMAR, "minä menen kauppaan eilen", "minä menin kauppaan eilen"),
                EssayIssue(EssayIssueKind.TYPO, "kirjastoo", "kirjastoon"),
            ),
            feedback = "Good structure. Watch your past tense.",
        )
    }
}