package me.longng.finnish_learning_mobile.data.auth

/**
 * Persistence facade for the user's auth session.
 */
interface TokenStore {
    /** Current JWT or null when the user is logged out. Called on every HTTP request via the interceptor. */
    fun read(): String?

    /** Full session (token + user id + username + role) or null when logged out. */
    fun readSession(): AuthSession?

    /** Persist a session. Overwrites any prior one. */
    fun write(session: AuthSession)

    /** Forget the session. Idempotent; safe to call when already logged out. */
    fun clear()
}