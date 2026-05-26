package me.longng.finnish_learning_mobile.data.api

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.Instant
import java.time.LocalDate

/**
 * Moshi adapter for [Instant]. The backend serializes `Instant` as an ISO-8601
 * string with a `Z` suffix (e.g. `2026-05-26T12:34:56.789Z`), which is exactly
 * what `Instant.toString()` produces and `Instant.parse()` consumes.
 */
class InstantAdapter {
    @ToJson
    fun toJson(value: Instant): String = value.toString()

    @FromJson
    fun fromJson(value: String): Instant = Instant.parse(value)
}

/**
 * Moshi adapter for [LocalDate]. The backend serializes `LocalDate` as
 * `YYYY-MM-DD`, the format `LocalDate.toString()` / `LocalDate.parse()` use
 * by default.
 */
class LocalDateAdapter {
    @ToJson
    fun toJson(value: LocalDate): String = value.toString()

    @FromJson
    fun fromJson(value: String): LocalDate = LocalDate.parse(value)
}