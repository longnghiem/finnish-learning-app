package me.longng.finnish_learning_backend.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import me.longng.finnish_learning_backend.controller.dto.EssayPromptResponse
import me.longng.finnish_learning_backend.controller.dto.EvaluateEssayRequest
import me.longng.finnish_learning_backend.controller.dto.EvaluateEssayResponse
import me.longng.finnish_learning_backend.service.EssayEvaluationService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/essays")
@Tag(name = "Essay Evaluation", description = "AI-backed Finnish short-essay evaluation")
class EssayEvaluationController(
    private val service: EssayEvaluationService,
) {


    /**
     * Lists the predefined essay prompts for one topic, for the essay page dropdown.
     */
    @GetMapping("/prompts")
    @Operation(
        summary = "List essay prompts for a topic",
        description = "Returns the predefined essay prompts shown in the essay page dropdown.",
    )
    @ApiResponse(responseCode = "200", description = "Prompts retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    fun getPrompts(@RequestParam topicId: Int): List<EssayPromptResponse> =
        service.getPrompts(topicId)

    /**
     * Evaluates a Finnish essay written by an authenticated user.
     *
     * @param request        payload carrying the chosen prompt id and the essay text.
     * @param authentication injected Spring Security context.
     * @return CEFR level, on-topic flag, concrete issues and optional prose feedback.
     */
    @PostMapping("/evaluate")
    @Operation(
        summary = "Evaluate a Finnish essay",
        description = "Returns a CEFR level, an on-topic flag, concrete grammar/typo issues " +
                "with suggested fixes, and optional holistic feedback.",
    )
    @ApiResponse(responseCode = "200", description = "Evaluation succeeded")
    @ApiResponse(responseCode = "400", description = "Blank essay, essay outside 300–2500 characters, or unknown prompt id")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "429", description = "Daily quota exceeded")
    @ApiResponse(responseCode = "502", description = "Upstream Bedrock failure or misconfiguration")
    fun evaluate(
        @RequestBody request: EvaluateEssayRequest,
        authentication: Authentication,
    ): EvaluateEssayResponse {
        val userId = authentication.principal as Int
        return service.evaluate(userId, request.promptId, request.essay)
    }
}