package me.longng.finnish_learning_backend.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import me.longng.finnish_learning_backend.controller.dto.DashboardResponse
import me.longng.finnish_learning_backend.controller.dto.TopicProgressResponse
import me.longng.finnish_learning_backend.service.ProgressService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Read-only progress API for authenticated users.
 *
 * All endpoints require an authenticated request.
 * They derive the user from the JWT, never from a request parameter,
 * so a user can only ever see their own progress.
 */
@RestController
@RequestMapping("api/progress")
@Tag(name = "Progress", description = "Learning progress and statistics for an authenticated user" )
class ProgressController (
    private val progressService: ProgressService,
) {

    @GetMapping("/topics")
    @Operation(summary = "Get per-topic progress for current user")
    @ApiResponse(responseCode = "200", description = "Progress retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    fun getAllTopicsProgress(authentication: Authentication): List<TopicProgressResponse> {
        val userId = authentication.principal as Int
        return progressService.getAllTopicsProgress(userId)
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get overall dashboard stats for current user")
    @ApiResponse(responseCode = "200", description = "Dashboard retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    fun getDashboard(authentication: Authentication): DashboardResponse {
        val userId = authentication.principal as Int
        return progressService.getDashboard(userId)
    }

}