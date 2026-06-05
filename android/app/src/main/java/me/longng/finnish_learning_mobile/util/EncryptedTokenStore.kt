package me.longng.finnish_learning_mobile.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import me.longng.finnish_learning_mobile.data.auth.AuthSession
import me.longng.finnish_learning_mobile.data.auth.TokenStore
import androidx.core.content.edit

/**
 * On-disk session store backed by `EncryptedSharedPreferences`.
 *
 * Encryption layout:
 *  * Master key — AES-256 GCM, generated and stored inside the Android Keystore.
 *    On most modern devices the key is hardware-backed (StrongBox or TEE);
 *    on older ones it falls back to software-only Keystore. Either way the
 *    app process never sees the raw key bytes.
 *  * Pref keys — encrypted with AES-256 SIV (deterministic, so reads can still
 *    find an entry by its key).
 *  * Pref values — encrypted with AES-256 GCM (authenticated, non-deterministic).
 *
 * Reads happen on every HTTP request via [AuthInterceptor]. They go through the
 * normal `SharedPreferences` cache after the first decryption, so the cost is
 * one AES op at process start — negligible.
 *
 * @param appContext **Always pass `applicationContext`.** Holding an Activity
 *   reference here would leak it for the lifetime of the singleton DI scope.
 */
class EncryptedTokenStore(appContext: Context) : TokenStore {

    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        FILE_NAME,
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun read(): String? = prefs.getString(KEY_TOKEN, null)

    override fun readSession(): AuthSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val userId = prefs.getInt(KEY_USER_ID, INVALID_USER_ID)
        check(userId != INVALID_USER_ID) { "Corrupted session: token present but no userId" }
        return AuthSession(
            token = token,
            userId = userId,
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            role = prefs.getString(KEY_ROLE, AuthSession.ROLE_USER) ?: AuthSession.ROLE_USER,
        )
    }

    override fun write(session: AuthSession) {
        // `commit()` over `apply()`: we want the write to be on disk before we
        // tell the caller "you're logged in". An app crash between `apply()` and
        // flush would lose the JWT and silently log the user out. The write is
        // tiny (~four ints/strings), so the synchronous cost is irrelevant.
        prefs.edit(commit = true) {
            putString(KEY_TOKEN, session.token)
                .putInt(KEY_USER_ID, session.userId)
                .putString(KEY_USERNAME, session.username)
                .putString(KEY_ROLE, session.role)
        }
    }

    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val FILE_NAME = "finnish_auth_prefs"
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "userId"
        const val KEY_USERNAME = "username"
        const val KEY_ROLE = "role"
        const val INVALID_USER_ID = -1
    }
}