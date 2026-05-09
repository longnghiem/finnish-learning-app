package me.longng.finnish_learning_backend.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import me.longng.finnish_learning_backend.controller.dto.AuthRequest
import me.longng.finnish_learning_backend.controller.dto.AuthResponse
import me.longng.finnish_learning_backend.service.AuthService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Authentication endpoints for user registration and login.
 *
 * Both endpoints are public (no JWT required). On success, they return
 * an [AuthResponse] containing a JWT token that the client must include
 * in subsequent requests as `Authorization: Bearer <token>`.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "User registration and login")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    @ApiResponse(responseCode = "201", description = "User registered successfully")
    @ApiResponse(responseCode = "400", description = "Invalid input (blank username or short password)")
    @ApiResponse(responseCode = "409", description = "Username already taken")
    fun register(@RequestBody request: AuthRequest): ResponseEntity<AuthResponse> {
        val response = authService.register(request.username, request.password)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive JWT token")
    @ApiResponse(responseCode = "200", description = "Login successful")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    fun login(@RequestBody request: AuthRequest): ResponseEntity<AuthResponse> {
        val response = authService.login(request.username, request.password)
        return ResponseEntity.ok(response)
    }
}