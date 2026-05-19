package me.longng.finnish_learning_backend.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import me.longng.finnish_learning_backend.controller.dto.EvaluateSentenceRequest
import me.longng.finnish_learning_backend.controller.dto.EvaluateSentenceResponse
import me.longng.finnish_learning_backend.service.SentenceEvaluationService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/evaluate-sentence")
@Tag(name = "Sentence Evaluation", description = "AI-backed Finnish sentence evaluation")
class SentenceEvaluationController(
    private val service: SentenceEvaluationService,
) {

    /**
     * Evaluates the inputted Finnish sentence from an authenticated user.
     *
     * @param request         payload carrying the user-typed Finnish sentence.
     * @param authentication  injected Spring Security context.
     * @return evaluation result with grammar / typo / CEFR-level feedback.
     */
    @PostMapping
    @Operation(
        summary = "Evaluate a Finnish sentence",
        description = "Returns grammar / typo status, CEFR level, optional correction and optional B1 example.",
    )
    @ApiResponse(responseCode = "200", description = "Evaluation succeeded")
    @ApiResponse(responseCode = "400", description = "Empty or blank sentence")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "429", description = "Daily quota exceeded")
    @ApiResponse(responseCode = "502", description = "Upstream AI failure or misconfiguration")
    fun evaluate(
        @RequestBody request: EvaluateSentenceRequest,
        authentication: Authentication
    ): EvaluateSentenceResponse {
        val userId = authentication.principal as Int
        return service.evaluate(userId, request.sentence)
    }
}
