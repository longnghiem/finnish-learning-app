package me.longng.finnish_learning_mobile.data.auth

interface TokenStore {
    /** Returns the currently-stored JWT, or null if the user is not logged in. */
    fun read(): String?
}