package me.longng.finnish_learning_backend.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import me.longng.finnish_learning_backend.config.WebSecurityConfig
import me.longng.finnish_learning_backend.controller.dto.ask.AdminDailyStatsResponse
import me.longng.finnish_learning_backend.controller.dto.ask.AdminSessionResponse
import me.longng.finnish_learning_backend.controller.dto.ask.AdminTurnPageResponse
import me.longng.finnish_learning_backend.controller.dto.ask.AdminTurnResponse
import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.domain.ask.TurnStatus
import me.longng.finnish_learning_backend.service.ask.AdminAskService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

/**
 * The owner's view of the recruiter chatbot: stored turns, sessions and daily totals, and deleting
 * a session. `ROLE_ADMIN` only: [WebSecurityConfig] refuses everyone else for `/api/admin/` (401
 * without a token, 403 with a non-admin one), before any of these methods runs.
 *
 * Dates are Europe/Helsinki calendar days in ISO format, e.g. `2026-10-29`. A wrong value (an
 * unknown mode, a bad date or id, `from` after `to`, a limit out of range) gets a 400.
 */
@RestController
@RequestMapping("/api/admin/ask")
@Tag(name = "Chatbot admin", description = "Stored chatbot conversations and statistics (ROLE_ADMIN)")
class AdminAskController(private val adminAskService: AdminAskService) {

    /**
     * Lists stored turns, newest first, one page at a time.
     */
    @GetMapping("/turns")
    @Operation(
        summary = "List chatbot turns",
        description = "Newest first. All filters are optional and combined with AND. " +
                "A page with fewer than `limit` turns is the last one.",
    )
    @ApiResponse(responseCode = "200", description = "One page of turns")
    @ApiResponse(responseCode = "400", description = "Invalid filter, or limit/offset out of range")
    @Parameter(name = "from", description = "First day to include (Helsinki time), e.g. 2026-10-01")
    @Parameter(name = "to", description = "Last day to include (Helsinki time), e.g. 2026-10-31")
    @Parameter(name = "limit", description = "Page size, 1 to 100")
    @Parameter(name = "offset", description = "How many newer turns to skip")
    fun listTurns(
        @RequestParam(required = false) mode: AskMode?,
        @RequestParam(required = false) status: TurnStatus?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): AdminTurnPageResponse {
        val turns = adminAskService.listTurns(mode, status, from, to, limit, offset)
        return AdminTurnPageResponse(turns = turns.map { AdminTurnResponse.from(it) }, limit = limit, offset = offset)
    }

    /**
     * Returns one session with all its turns.
     */
    @GetMapping("/sessions/{id}")
    @Operation(summary = "Get a chatbot session with all its turns")
    @ApiResponse(responseCode = "200", description = "Session found")
    @ApiResponse(responseCode = "404", description = "Session not found (or already purged)")
    fun getSession(@PathVariable id: UUID): AdminSessionResponse =
        AdminSessionResponse.from(adminAskService.session(id))

    /**
     * Deletes a session and all its turns.
     */
    @DeleteMapping("/sessions/{id}")
    @Operation(
        summary = "Delete a chatbot session",
        description = "Deletes the session and all its turns. The daily totals stay.",
    )
    @ApiResponse(responseCode = "204", description = "Session deleted")
    @ApiResponse(responseCode = "404", description = "Session not found (or already purged)")
    fun deleteSession(@PathVariable id: UUID): ResponseEntity<Void> {
        adminAskService.deleteSession(id)
        return ResponseEntity.noContent().build()
    }

    /**
     * Returns the daily totals of a range of days.
     */
    @GetMapping("/daily-stats")
    @Operation(
        summary = "Daily chatbot totals",
        description = "Oldest first; days without activity are missing. Without `to` the range ends " +
                "today; without `from` it covers 30 days.",
    )
    @ApiResponse(responseCode = "200", description = "The days' totals")
    @ApiResponse(responseCode = "400", description = "Invalid date, or from after to")
    fun dailyStats(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): List<AdminDailyStatsResponse> =
        adminAskService.dailyStats(from, to).map { AdminDailyStatsResponse.from(it) }

    private companion object {
        /** The page size when the request names none; the admin table loads 50 at a time. */
        const val DEFAULT_PAGE_SIZE = "50"
    }
}