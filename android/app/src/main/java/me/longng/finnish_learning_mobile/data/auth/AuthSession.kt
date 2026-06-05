package me.longng.finnish_learning_mobile.data.auth

/**
 * Cached snapshot of a logged-in user.
 *
 * Stored on disk by [EncryptedTokenStore] and exposed in memory through [AuthRepository.session]
 */
data class AuthSession(
    val token: String,
    val userId: Int,
    val username: String,
    val role: String,
) {
    val isAdmin: Boolean get() = role == ROLE_ADMIN

    companion object {
        const val ROLE_ADMIN = "ADMIN"
        const val ROLE_USER = "USER"
    }
}