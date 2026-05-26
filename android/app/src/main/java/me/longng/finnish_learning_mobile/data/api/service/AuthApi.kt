package me.longng.finnish_learning_mobile.data.api.service

import me.longng.finnish_learning_mobile.data.api.dto.AuthRequest
import me.longng.finnish_learning_mobile.data.api.dto.AuthResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("api/auth/register")
    suspend fun register(@Body body: AuthRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body body: AuthRequest): AuthResponse
}