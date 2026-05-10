package me.longng.finnish_learning_backend.controller

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.controller.dto.SubmitAnswerRequest
import me.longng.finnish_learning_backend.domain.Role
import me.longng.finnish_learning_backend.persistence.CardRepository
import me.longng.finnish_learning_backend.persistence.ReviewScheduleRepository
import me.longng.finnish_learning_backend.persistence.TopicRepository
import me.longng.finnish_learning_backend.persistence.UserRepository
import me.longng.finnish_learning_backend.service.JwtService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@Transactional
class QuizControllerTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var topicRepository: TopicRepository

    @Autowired
    private lateinit var cardRepository: CardRepository

    @Autowired
    private lateinit var reviewScheduleRepository: ReviewScheduleRepository

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private var userId: Int = 0
    private lateinit var token: String
    private var topicId: Int = 0

    @BeforeEach
    fun setup() {
        // Create a test user
        val user = userRepository.insert(
            username = "quizuser",
            passwordHash = passwordEncoder.encode("password123")!!,
            role = Role.USER,
        )
        userId = user.id
        token = jwtService.generateToken(user.id, user.username, user.role)

        topicId = topicRepository.findAll().last().id
    }

    private fun createCard(name: String): Int {
        val card = cardRepository.insert(
            topicId = topicId,
            name = name,
            exampleSentence = "Example for $name",
            translation = "Translation of $name",
            imageFilename = "test-image.png",
        )
        return card.id
    }

    @Test
    fun testGetQuizCards_ReturnsNewCards() {
        // Create cards with no review history — they should appear as "new"
        createCard("uusi1")
        createCard("uusi2")

        mockMvc.get("/api/quiz/topics/$topicId/cards") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].isNew") { value(true) }
            jsonPath("$[0].repetition") { doesNotExist() }
            jsonPath("$[1].isNew") { value(true) }
        }
    }

    @Test
    fun testGetQuizCards_DueCardsBeforeNewCards() {
        val card1Id = createCard("due-card")
        val card2Id = createCard("new-card-1")
        val card3Id = createCard("new-card-2")

        // Mark card1 as due (next_review_date = yesterday)
        reviewScheduleRepository.upsert(
            userId = userId,
            cardId = card1Id,
            repetition = 1,
            easeFactor = 2.5,
            intervalDays = 1,
            nextReviewDate = LocalDate.now().minusDays(1),
        )

        mockMvc.get("/api/quiz/topics/$topicId/cards?limit=10") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            // First card should be the due card (not new)
            jsonPath("$[0].cardId") { value(card1Id) }
            jsonPath("$[0].isNew") { value(false) }
            jsonPath("$[0].repetition") { value(1) }
            // Remaining cards should be new
            jsonPath("$[1].isNew") { value(true) }
            jsonPath("$[2].isNew") { value(true) }
        }
    }

    @Test
    fun testGetQuizCards_RespectsLimit() {
        createCard("card1")
        createCard("card2")
        createCard("card3")

        mockMvc.get("/api/quiz/topics/$topicId/cards?limit=2") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
        }
    }

    @Test
    fun testGetQuizCards_EmptyTopic() {
        // Topic exists but has no cards — should return empty list
        mockMvc.get("/api/quiz/topics/$topicId/cards") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun testGetQuizCards_InvalidLimit() {
        mockMvc.get("/api/quiz/topics/$topicId/cards?limit=0") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun testGetQuizCards_Unauthenticated() {
        mockMvc.get("/api/quiz/topics/$topicId/cards") {
            // No Authorization header
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun testSubmitAnswer_FirstReview() {
        val cardId = createCard("ensimmäinen")

        mockMvc.post("/api/quiz/answer") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(SubmitAnswerRequest(cardId, 4))
        }.andExpect {
            status { isOk() }
            jsonPath("$.cardId") { value(cardId) }
            jsonPath("$.correct") { value(true) }
            // First correct answer with quality=4: repetition=1, interval=1
            jsonPath("$.repetition") { value(1) }
            jsonPath("$.intervalDays") { value(1) }
            jsonPath("$.easeFactor") { isNumber() }
            jsonPath("$.nextReviewDate") { exists() }
        }
    }

    @Test
    fun testSubmitAnswer_IncorrectAnswer() {
        val cardId = createCard("vaikea")

        // First, establish a schedule with some progress
        reviewScheduleRepository.upsert(
            userId = userId,
            cardId = cardId,
            repetition = 3,
            easeFactor = 2.5,
            intervalDays = 15,
            nextReviewDate = LocalDate.now(),
        )

        // Submit an incorrect answer (quality=1 = "Again")
        mockMvc.post("/api/quiz/answer") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(SubmitAnswerRequest(cardId, 1))
        }.andExpect {
            status { isOk() }
            jsonPath("$.correct") { value(false) }
            // SM-2 resets on incorrect: repetition=0, interval=1
            jsonPath("$.repetition") { value(0) }
            jsonPath("$.intervalDays") { value(1) }
        }
    }

    @Test
    fun testSubmitAnswer_CardNotFound() {
        mockMvc.post("/api/quiz/answer") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(SubmitAnswerRequest(99999, 4))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun testSubmitAnswer_InvalidQuality() {
        val cardId = createCard("testi")

        mockMvc.post("/api/quiz/answer") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(SubmitAnswerRequest(cardId, 6))
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun testSubmitAnswer_Unauthenticated() {
        mockMvc.post("/api/quiz/answer") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(SubmitAnswerRequest(1, 4))
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun testSubmitAnswer_SchedulePersisted() {
        val cardId = createCard("tallennettu")

        // Submit answer
        mockMvc.post("/api/quiz/answer") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(SubmitAnswerRequest(cardId, 5))
        }.andExpect {
            status { isOk() }
        }

        // Verify the schedule was persisted by fetching quiz cards —
        // the card should no longer appear as "new"
        mockMvc.get("/api/quiz/topics/$topicId/cards") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            // Card was reviewed with quality=5, so next_review_date = tomorrow.
            // It should NOT appear in due cards (it's not due yet) and NOT as new.
            jsonPath("$.length()") { value(0) }
        }
    }

}