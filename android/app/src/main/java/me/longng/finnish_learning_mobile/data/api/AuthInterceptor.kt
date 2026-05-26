package me.longng.finnish_learning_mobile.data.api

import me.longng.finnish_learning_mobile.data.auth.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp application interceptor that attaches `Authorization: Bearer <jwt>`
 * when the user is logged in. Public endpoints (browse, image fetch) tolerate
 * the header — the backend ignores it when not required.
 *
 * Centralizing auth in an interceptor keeps every Retrofit method signature
 * free of `@Header("Authorization")` parameters.
 */
class AuthInterceptor (private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenStore.read()
        val request = if (token != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        return chain.proceed(request)
    }
}