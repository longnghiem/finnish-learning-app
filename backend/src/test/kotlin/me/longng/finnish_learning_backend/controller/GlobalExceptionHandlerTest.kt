package me.longng.finnish_learning_backend.controller

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class GlobalExceptionHandlerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun testMalformedBody_NotJson() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = "abc"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.status") { value(400) }
            jsonPath("$.message") { value("Malformed or missing request body.") }
        }
    }

    @Test
    fun testMalformedBody_MissingField() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"a"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun testMalformedBody_NullField() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":null,"password":"x"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun testMalformedBody_Empty() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun testMalformedBody_LeaksNoInternals() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"a"}"""
        }.andExpect {
            status { isBadRequest() }
            content { string(not(containsString("me.longng"))) }
            content { string(not(containsString("AuthRequest"))) }
            content { string(not(containsString("password"))) }
        }
    }

    @Test
    fun testTypeMismatch_QueryParam() {
        mockMvc.get("/api/cards?topicId=abc").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Invalid value for parameter 'topicId'.") }
            content { string(not(containsString("abc"))) }
        }
    }

    @Test
    fun testUnsupportedMediaType() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.TEXT_PLAIN
            content = "abc"
        }.andExpect {
            status { isUnsupportedMediaType() }
        }
    }

    @Test
    fun testMethodNotAllowed() {
        mockMvc.patch("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"a","password":"b"}"""
        }.andExpect {
            status { isMethodNotAllowed() }
        }
    }

    @Test
    fun testUnknownPath() {
        mockMvc.get("/api/topics/no-such-thing").andExpect {
            status { isNotFound() }
        }
    }
}