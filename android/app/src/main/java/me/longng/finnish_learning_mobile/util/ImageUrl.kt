package me.longng.finnish_learning_mobile.util

import me.longng.finnish_learning_mobile.BuildConfig

/**
 * The backend returns image URLs as relative paths like `/api/images/foo.png`.
 * Coil requires absolute URLs, so we prepend [BuildConfig.API_BASE_URL] with
 * any duplicate trailing slash stripped.
 *
 * Example:
 *   relative = "/api/images/cat.png"
 *   returns  = "http://10.0.2.2:8080/api/images/cat.png"
 */
fun absoluteImageUrl(relative: String): String =
    BuildConfig.API_BASE_URL.trimEnd('/') + relative