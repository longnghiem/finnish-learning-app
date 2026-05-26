package me.longng.finnish_learning_mobile.data.api.dto

import com.squareup.moshi.JsonClass
import java.time.Instant

/**
 * A single Finnish vocabulary card.
 *
 * @property imageUrl Relative server path like `/api/images/abc.jpg`.
 *                    Prepend `BuildConfig.API_BASE_URL` before handing to Coil.
 *                    Backend guarantees this is non-null.
 * @property createdAt / [updatedAt] Server-side timestamps in ISO-8601.
 *                    Parsed by [me.longng.finnish_learning_mobile.data.api.InstantAdapter].
 */
@JsonClass(generateAdapter = true)
data class CardResponse(
    val id: Int,
    val topicId: Int,
    val name: String,
    val exampleSentence: String,
    val translation: String,
    val imageUrl: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Mirrors the backend `SearchType` enum
 */
enum class SearchType { VERB, SENTENCE }