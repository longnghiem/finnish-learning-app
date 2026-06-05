package me.longng.finnish_learning_mobile.data.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import me.longng.finnish_learning_mobile.data.api.dto.AuthRequest
import me.longng.finnish_learning_mobile.data.api.dto.AuthResponse
import me.longng.finnish_learning_mobile.data.api.service.AuthApi
import me.longng.finnish_learning_mobile.util.AppResult
import me.longng.finnish_learning_mobile.util.runCatchingApp

/**
 * Single source of truth for the user's auth state.
 *
 * Responsibilities:
 *  * Translate Retrofit calls into typed [AppResult]s.
 *  * Persist successful logins via [TokenStore].
 *  * Expose [session] so the rest of the app reacts to login / logout without polling.
 *
 * Lifecycle: this class is a process-wide singleton (bound in Hilt in TASK_09).
 * The in-memory [_state] is hydrated from disk in the init block so a warm
 * cold-start picks up the previous session immediately — no flicker through
 * a logged-out UI state.
 */
class AuthRepository(
    private val api: AuthApi,
    private val store: TokenStore,
) {

    private val _state = MutableStateFlow(store.readSession())

    /** Current session or null when logged out. Hot — always has a value. */
    val session: StateFlow<AuthSession?> = _state.asStateFlow()

    /** Boolean projection for screens that only care about logged-in / logged-out. */
    val isLoggedIn: Flow<Boolean> = _state.map { it != null }

    suspend fun login(username: String, password: String): AppResult<AuthSession> {
        require(username.isNotBlank()) { "username must not be blank" }
        require(password.isNotBlank()) { "password must not be blank" }
        return runCatchingApp {
            val response = api.login(AuthRequest(username, password))
            response.toSession().also(::persist)
        }
    }

    suspend fun register(username: String, password: String): AppResult<AuthSession> {
        require(username.isNotBlank()) { "username must not be blank" }
        require(password.isNotBlank()) { "password must not be blank" }
        return runCatchingApp {
            val response = api.register(AuthRequest(username, password))
            response.toSession().also(::persist)
        }
    }

    /**
     * Clears the session both in memory and on disk. Synchronous and never fails.
     * Call this when the user taps "logout" or when an interceptor / repo sees a
     * 401 ([AppError.Unauthorized][me.longng.finnish_learning_mobile.util.AppError.Unauthorized]).
     */
    fun logout() {
        store.clear()
        _state.value = null
    }

    private fun persist(session: AuthSession) {
        store.write(session)
        _state.value = session
    }

    private fun AuthResponse.toSession() = AuthSession(
        token = token,
        userId = userId,
        username = username,
        role = role,
    )
}