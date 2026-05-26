package me.longng.finnish_learning_mobile.data.api

import com.squareup.moshi.Moshi

object MoshiProvider {
    fun create(): Moshi = Moshi.Builder()
        .add(InstantAdapter())
        .add(LocalDateAdapter())
        .build()
}