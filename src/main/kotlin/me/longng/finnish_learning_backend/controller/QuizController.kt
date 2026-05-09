package me.longng.finnish_learning_backend.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import me.longng.finnish_learning_backend.controller.dto.QuizCardResponse
import me.longng.finnish_learning_backend.controller.dto.SubmitAnswerRequest
import me.longng.finnish_learning_backend.controller.dto.SubmitAnswerResponse
import me.longng.finnish_learning_backend.service.QuizService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for the spaced repetition quiz feature.
 *
 * Endpoints:
 * - GET  /api/quiz/topics/{topicId}/cards — fetch cards due for review
 * - POST /api/quiz/answer — submit a quiz answer and update the schedule
*/
@RestController
@RequestMapping("/api/quiz")
@Tag(name = "Quiz", description = "Spaced repetition quiz endpoints")
class QuizController(private val quizService: QuizService) {

    @GetMapping("/topics/{topicId}/cards")
    @Operation(
        summary = "Get cards due for review",
        description = "Returns due cards first (most overdue), then new cards, up to the limit.",
    )
    @ApiResponse(responseCode = "200", description = "Quiz cards retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    fun getQuizCards(
        @PathVariable topicId: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        authentication: Authentication,
    ): List<QuizCardResponse> {
        // The principal is set to userId (Int) by JwtAuthenticationFilter.
        // This cast is safe because /api/quiz/** requires authentication —
        // unauthenticated requests never reach this controller.
        val userId = authentication.principal as Int
        return quizService.getQuizCards(userId, topicId, limit)
    }

    @PostMapping("/answer")
    @Operation(
        summary = "Submit a quiz answer",
        description = "Updates the spaced repetition schedule and publishes an event for stats aggregation.",
    )
    @ApiResponse(responseCode = "200", description = "Answer processed, schedule updated")
    @ApiResponse(responseCode = "400", description = "Invalid quality value")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Card not found")
    fun submitAnswer(
        @RequestBody request: SubmitAnswerRequest,
        authentication: Authentication,
    ): SubmitAnswerResponse {
        val userId = authentication.principal as Int
        return quizService.submitAnswer(userId, request.cardId, request.quality)
    }
}