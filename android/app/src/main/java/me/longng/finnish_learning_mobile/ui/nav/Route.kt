package me.longng.finnish_learning_mobile.ui.nav

import kotlinx.serialization.Serializable

/**
 * Type-safe representation of every destination in the app.
 */
object Route {
    @Serializable data object Landing
    @Serializable data object Login
    @Serializable data object Register
    @Serializable data object Dashboard
    @Serializable data object Portfolio

    @Serializable data class Topic(val topicId: Int)
    @Serializable data class Quiz(val topicId: Int)
}