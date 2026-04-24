package me.longng.finnish_learning_backend.controller

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class TopicControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun testGetTopics() {
        mockMvc.get("/api/topics")
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(7) }
                jsonPath("$[0].id") { exists() }
                jsonPath("$[0].name") { exists() }
            }
    }
}