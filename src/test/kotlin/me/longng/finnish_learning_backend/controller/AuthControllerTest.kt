package me.longng.finnish_learning_backend.controller

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.controller.dto.AuthRequest
import me.longng.finnish_learning_backend.domain.Role
import me.longng.finnish_learning_backend.persistence.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@Transactional
class AuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    private fun registerRequest(username: String = "testuser", password: String = "password123"): String =
        objectMapper.writeValueAsString(AuthRequest(username, password))

    private fun insertUser(
        username: String = "existinguser",
        password: String = "password123",
        role: Role = Role.USER,
    ) {
        userRepository.insert(
            username = username,
            passwordHash = checkNotNull(passwordEncoder.encode(password)),
            role = role,
        )
    }

    @Test
    fun testRegister() {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest("newuser", "password123")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.token") { exists() }
            jsonPath("$.userId") { exists() }
            jsonPath("$.username") { value("newuser") }
            jsonPath("$.role") { value("USER") }
        }
    }

    @Test
    fun testRegister_DuplicateUsername() {
        insertUser(username = "duplicate")

        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest("duplicate", "password123")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.message") { value("Username 'duplicate' is already taken") }
        }
    }

    @Test
    fun testRegister_BlankUsername() {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest("", "password123")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun testRegister_PasswordTooShort() {
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest("newuser", "12345")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun testLogin() {
        insertUser(username = "loginuser", password = "mypassword")

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest("loginuser", "mypassword")
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { exists() }
            jsonPath("$.userId") { exists() }
            jsonPath("$.username") { value("loginuser") }
            jsonPath("$.role") { value("USER") }
        }
    }

    @Test
    fun testLogin_NonExistentUsername() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest("nobody", "password123")
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.message") { value("Invalid username or password") }
        }
    }

    @Test
    fun testLogin_WrongPassword() {
        insertUser(username = "loginuser", password = "correctpassword")

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest("loginuser", "wrongpassword")
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.message") { value("Invalid username or password") }
        }
    }

    @Test
    fun testLogin_AdminRole() {
        insertUser(username = "admin", password = "adminpass", role = Role.ADMIN)

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = registerRequest("admin", "adminpass")
        }.andExpect {
            status { isOk() }
            jsonPath("$.role") { value("ADMIN") }
        }
    }
}