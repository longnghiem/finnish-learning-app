package me.longng.finnish_learning_mobile.util

import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

sealed interface AppResult<out T> {
    data class Success<T>(val value: T): AppResult<T>
    data class Failure(val error: AppError): AppResult<Nothing>
}

sealed interface AppError {
    data object Network : AppError
    data class Http(val code:Int, val message: String?): AppError
    data object Unauthorized: AppError
    data class Unknown(val cause: Throwable): AppError
}

/**
 * Run a suspend-or-blocking block and translate exceptions into [AppResult].
 */
inline fun <T> runCatchingApp(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: HttpException) {
    if (e.code() == 401) AppResult.Failure(AppError.Unauthorized)
    else AppResult.Failure(AppError.Http(e.code(), e.message()))
} catch (e: IOException) {
    AppResult.Failure(AppError.Network)
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    AppResult.Failure(AppError.Unknown(e))
}