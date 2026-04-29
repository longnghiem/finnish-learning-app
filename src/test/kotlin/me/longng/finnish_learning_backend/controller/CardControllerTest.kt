package me.longng.finnish_learning_backend.controller

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.controller.dto.CardQueryParams
import me.longng.finnish_learning_backend.controller.dto.SearchType
import me.longng.finnish_learning_backend.domain.Role
import me.longng.finnish_learning_backend.persistence.CardRepository
import me.longng.finnish_learning_backend.persistence.TopicRepository
import me.longng.finnish_learning_backend.persistence.UserRepository
import me.longng.finnish_learning_backend.service.JwtService
import me.longng.finnish_learning_backend.storage.ImageStorageService
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.transaction.annotation.Transactional


@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@Transactional
class CardControllerTest {
    @MockitoBean
    private lateinit var imageStorageService: ImageStorageService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var topicRepository: TopicRepository

    @Autowired
    private lateinit var cardRepository: CardRepository

    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    private fun firstTopicId(): Int = topicRepository.findAll().first().id

    private fun testImage(): MockMultipartFile = MockMultipartFile(
        "image",
        "test-image.jpg",
        "image/jpeg",
        "fake-image-content".toByteArray(),
    )

    private fun createTestCard(
        name: String = "syödä",
        exampleSentence: String = "Minä syön jäätelöä",
        translation: String = "to eat",
        topicId: Int = firstTopicId(),
        token: String,
    ): Int {
        mockMvc.multipart("/api/cards") {
            file(testImage())
            param("name", name)
            param("exampleSentence", exampleSentence)
            param("translation", translation)
            param("topicId", topicId.toString())
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isCreated() }
        }

        val cardId = cardRepository.findAll(CardQueryParams(
            topicId = null,
            searchType = SearchType.VERB,
            searchTerm = name,
        )).first().id

        return cardId
    }

    /**
     * Creates a test admin user and returns a valid JWT token for that user.
     * Card mutation endpoints (POST/PUT/DELETE) require ADMIN role.
     */
    private fun adminToken(): String {
        val user = userRepository.insert(
            username = "testadmin",
            passwordHash = passwordEncoder.encode("password")!!,
            role = Role.ADMIN,
        )
        return jwtService.generateToken(user.id, user.username, user.role)
    }

    @BeforeEach
    fun setUp() {
        whenever(imageStorageService.store(any())).thenReturn("test-image.jpg")
    }

    @Test
    fun testCreateCard() {
        val token = adminToken()

        mockMvc.multipart("/api/cards") {
            file(testImage())
            param("name", "syödä")
            param("exampleSentence", "Minä syön jäätelöä")
            param("translation", "to eat")
            param("topicId", firstTopicId().toString())
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
            jsonPath("$.name") { value("syödä") }
            jsonPath("$.exampleSentence") { value("Minä syön jäätelöä") }
            jsonPath("$.translation") { value("to eat") }
            jsonPath("$.topicId") { value(firstTopicId()) }
            jsonPath("$.imageUrl") { value(startsWith("/api/images/")) }
            jsonPath("$.createdAt") { exists() }
            jsonPath("$.updatedAt") { exists() }
        }
    }

    @Test
    fun testCreateCard_MissingRequiredFields() {
        val token = adminToken()

        mockMvc.multipart("/api/cards") {
            file(testImage())
            param("exampleSentence", "Minä syön jäätelöä")
            param("translation", "to eat")
            param("topicId", firstTopicId().toString())
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isBadRequest() }
        }

        mockMvc.multipart("/api/cards") {
            param("name", "syödä")
            param("exampleSentence", "Minä syön jäätelöä")
            param("translation", "to eat")
            param("topicId", firstTopicId().toString())
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun testCreateCard_NonExistingTopic() {
        val token = adminToken()

        mockMvc.multipart("/api/cards") {
            file(testImage())
            param("name", "syödä")
            param("exampleSentence", "Minä syön jäätelöä")
            param("translation", "to eat")
            param("topicId", "99999")
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun testGetCardById() {
        val token = adminToken()
        val cardId = createTestCard(token = token)

        mockMvc.get("/api/cards/$cardId")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(cardId) }
                jsonPath("$.name") { value("syödä") }
            }
    }

    @Test
    fun testGetCardById_NotFound() {
        mockMvc.get("/api/cards/99999")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun testUpdateCard() {
        val token = adminToken()
        val cardId = createTestCard(token = token)

        mockMvc.multipart("/api/cards/$cardId") {
            with { request -> request.method = "PUT"; request }
            param("name", "juoda")
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(cardId) }
            jsonPath("$.name") { value("juoda") }
            jsonPath("$.translation") { value("to eat") }
        }
    }

    @Test
    fun testDeleteCard() {
        val token = adminToken()
        val cardId = createTestCard(token = token)

        mockMvc.delete("/api/cards/$cardId") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isNoContent() }
        }

        mockMvc.get("/api/cards/$cardId")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun testQueryCards_ByTopic() {
        val token = adminToken()
        val topicId = topicRepository.findAll()[2].id
        val anotherTopicId = topicRepository.findAll()[3].id
        createTestCard(name = "syödä", topicId = topicId, token = token)
        createTestCard(name = "juoda", topicId = topicId, token = token)
        createTestCard(name = "tavata", topicId = anotherTopicId, token = token)

        mockMvc.get("/api/cards") {
            param("topicId", topicId.toString())
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
        }
    }

    @Test
    fun testQueryCards_BySearchTerm() {
        val token = adminToken()
        createTestCard(name = "harrastaa", exampleSentence = "Minä harrastan sulkapalloa", token = token)
        createTestCard(name = "juoda", exampleSentence = "Hän juo maitoa", token = token)

        mockMvc.get("/api/cards") {
            param("searchType", "VERB")
            param("searchTerm", "harrastaa")
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].name") { value("harrastaa") }
        }
    }

    @Test
    fun testCreateCard_WithoutToken_Returns401() {
        mockMvc.multipart("/api/cards") {
            file(testImage())
            param("name", "syödä")
            param("exampleSentence", "Minä syön jäätelöä")
            param("translation", "to eat")
            param("topicId", firstTopicId().toString())
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun testCreateCard_WithUserRole_Returns403() {
        val user = userRepository.insert(
            username = "regularuser",
            passwordHash = passwordEncoder.encode("password")!!,
            role = Role.USER,
        )
        val userToken = jwtService.generateToken(user.id, user.username, user.role)

        mockMvc.multipart("/api/cards") {
            file(testImage())
            param("name", "syödä")
            param("exampleSentence", "Minä syön jäätelöä")
            param("translation", "to eat")
            param("topicId", firstTopicId().toString())
            header("Authorization", "Bearer $userToken")
        }.andExpect {
            status { isForbidden() }
        }
    }
}