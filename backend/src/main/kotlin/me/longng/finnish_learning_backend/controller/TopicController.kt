package me.longng.finnish_learning_backend.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import me.longng.finnish_learning_backend.controller.dto.TopicResponse
import me.longng.finnish_learning_backend.persistence.CardRepository
import me.longng.finnish_learning_backend.service.TopicService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/topics")
@Tag(name = "Topics", description = "Read-only access to the predefined Finnish learning topics")
class TopicController(
    private val topicService: TopicService,
    private val cardRepository: CardRepository,
) {

    @GetMapping
    @Operation(summary = "List all topics", description = "Returns all 7 predefined Finnish learning topics")
    @ApiResponse(responseCode = "200", description = "Topics retrieved successfully")
    fun getAllTopics(): List<TopicResponse> =
        topicService.getAllTopics().map { TopicResponse.from(it, cardRepository.countByTopicId(it.id)) }
}
