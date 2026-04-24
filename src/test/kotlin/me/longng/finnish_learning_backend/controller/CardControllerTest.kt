package me.longng.finnish_learning_backend.controller

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.controller.dto.CardQueryParams
import me.longng.finnish_learning_backend.domain.SearchType
import me.longng.finnish_learning_backend.persistence.CardRepository
import me.longng.finnish_learning_backend.persistence.TopicRepository
import me.longng.finnish_learning_backend.storage.ImageStorageService
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.transaction.annotation.Transactional
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever


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
    private lateinit var topicRepository: TopicRepository

    @Autowired
    private lateinit var cardRepository: CardRepository

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
    ): Int {
        mockMvc.multipart("/api/cards") {
            file(testImage())
            param("name", name)
            param("exampleSentence", exampleSentence)
            param("translation", translation)
            param("topicId", topicId.toString())
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

    @BeforeEach
    fun setUp() {
        whenever(imageStorageService.store(any())).thenReturn("test-image.jpg")
    }

    @Test
    fun testCreateCard() {
        mockMvc.multipart("/api/cards") {
            file(testImage())
            param("name", "syödä")
            param("exampleSentence", "Minä syön jäätelöä")
            param("translation", "to eat")
            param("topicId", firstTopicId().toString())
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
        mockMvc.multipart("/api/cards") {
            file(testImage())
            param("exampleSentence", "Minä syön jäätelöä")
            param("translation", "to eat")
            param("topicId", firstTopicId().toString())
        }.andExpect {
            status { isBadRequest() }
        }

        mockMvc.multipart("/api/cards") {
            param("name", "syödä")
            param("exampleSentence", "Minä syön jäätelöä")
            param("translation", "to eat")
            param("topicId", firstTopicId().toString())
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun testCreateCard_NonExistingTopic() {
        mockMvc.multipart("/api/cards") {
            file(testImage())
            param("name", "syödä")
            param("exampleSentence", "Minä syön jäätelöä")
            param("translation", "to eat")
            param("topicId", "99999")
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun testGetCardById() {
        val cardId = createTestCard()

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
        val cardId = createTestCard()

        mockMvc.multipart("/api/cards/$cardId") {
            with { request -> request.method = "PUT"; request }
            param("name", "juoda")
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(cardId) }
            jsonPath("$.name") { value("juoda") }
            jsonPath("$.translation") { value("to eat") }
        }
    }

    @Test
    fun testDeleteCard() {
        val cardId = createTestCard()

        mockMvc.delete("/api/cards/$cardId")
            .andExpect {
                status { isNoContent() }
            }

        mockMvc.get("/api/cards/$cardId")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun testQueryCards_ByTopic() {
        val topicId = firstTopicId()
        val anotherTopicId = topicRepository.findAll()[3].id
        createTestCard(name = "syödä", topicId = topicId)
        createTestCard(name = "juoda", topicId = topicId)
        createTestCard(name = "tavata", topicId = anotherTopicId)

        mockMvc.get("/api/cards") {
            param("topicId", topicId.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
        }
    }

    @Test
    fun testQueryCards_BySearchTerm() {
        createTestCard(name = "syödä", exampleSentence = "Minä syön jäätelöä")
        createTestCard(name = "juoda", exampleSentence = "Hän juo maitoa")

        mockMvc.get("/api/cards") {
            param("searchType", "VERB")
            param("searchTerm", "syö")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].name") { value("syödä") }
        }
    }

}