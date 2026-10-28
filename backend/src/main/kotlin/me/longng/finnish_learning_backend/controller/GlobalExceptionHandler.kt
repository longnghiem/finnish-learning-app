package me.longng.finnish_learning_backend.controller

import com.fasterxml.jackson.annotation.JsonInclude
import me.longng.finnish_learning_backend.controller.dto.ask.AskErrorCode
import me.longng.finnish_learning_backend.controller.dto.ask.AskErrorEvent
import me.longng.finnish_learning_backend.service.CardNotFoundException
import me.longng.finnish_learning_backend.service.InvalidCredentialsException
import me.longng.finnish_learning_backend.service.TopicNotFoundException
import me.longng.finnish_learning_backend.service.UsernameAlreadyExistsException
import me.longng.finnish_learning_backend.service.ask.AskConversationLimitException
import me.longng.finnish_learning_backend.service.ask.AskRateLimitedException
import me.longng.finnish_learning_backend.service.ask.AskSessionInvalidException
import me.longng.finnish_learning_backend.service.ask.TurnstileVerificationException
import me.longng.finnish_learning_backend.service.bedrock.EssayEvaluationMisconfiguredException
import me.longng.finnish_learning_backend.service.bedrock.EssayEvaluationQuotaExceededException
import me.longng.finnish_learning_backend.service.bedrock.EssayEvaluationUpstreamException
import me.longng.finnish_learning_backend.service.groq.SentenceEvaluationMisconfiguredException
import me.longng.finnish_learning_backend.service.groq.SentenceEvaluationQuotaExceededException
import me.longng.finnish_learning_backend.service.groq.SentenceEvaluationUpstreamException
import me.longng.finnish_learning_backend.storage.ImageStorageException
import org.springframework.security.access.AccessDeniedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.context.request.async.AsyncRequestTimeoutException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * Central exception-to-HTTP-status mapping for the whole API.
 * All responses use [ErrorResponse].
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * The error response body returned for all handled exceptions.
     */
    data class ErrorResponse(
        val status: Int,
        val error: String,
        val message: String,
        @field:JsonInclude(JsonInclude.Include.NON_NULL)
        val code: String? = null,
    )

    @ExceptionHandler(CardNotFoundException::class)
    fun handleCardNotFound(ex: CardNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("Card not found: {}", ex.message)
        return buildResponse(HttpStatus.NOT_FOUND, ex.message)
    }

    @ExceptionHandler(TopicNotFoundException::class)
    fun handleTopicNotFound(ex: TopicNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("Topic not found: {}", ex.message)
        return buildResponse(HttpStatus.NOT_FOUND, ex.message)
    }

    @ExceptionHandler(ImageStorageException::class)
    fun handleImageStorage(ex: ImageStorageException): ResponseEntity<ErrorResponse> {
        logger.error("Image storage operation failed", ex)
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Image storage operation failed")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        logger.warn("Validation failed: {}", message)
        return buildResponse(HttpStatus.BAD_REQUEST, message)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(ex: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> {
        logger.warn("Missing request parameter: {}", ex.message)
        return buildResponse(HttpStatus.BAD_REQUEST, ex.message)
    }

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingPart(ex: MissingServletRequestPartException): ResponseEntity<ErrorResponse> {
        logger.warn("Missing request part: {}", ex.message)
        return buildResponse(HttpStatus.BAD_REQUEST, ex.message)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn("Illegal argument: {}", ex.message)
        return buildResponse(HttpStatus.BAD_REQUEST, ex.message)
    }

    @ExceptionHandler(UsernameAlreadyExistsException::class)
    fun handleUsernameExists(ex: UsernameAlreadyExistsException): ResponseEntity<ErrorResponse> {
        logger.warn("Registration failed: {}", ex.message)
        return buildResponse(HttpStatus.CONFLICT, ex.message)
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException): ResponseEntity<ErrorResponse> {
        logger.warn("Login failed: {}", ex.message)
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.message)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ErrorResponse> {
        logger.warn("Access denied: {}", ex.message)
        return buildResponse(HttpStatus.FORBIDDEN, "You do not have permission to perform this action")
    }

    @ExceptionHandler(SentenceEvaluationQuotaExceededException::class)
    fun handleSentenceEvaluationQuota(
        ex: SentenceEvaluationQuotaExceededException,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Sentence evaluation quota exceeded: {}", ex.message)
        return buildResponse(
            HttpStatus.TOO_MANY_REQUESTS,
            "Daily limit of ${ex.dailyLimit} sentence evaluations reached. Try again tomorrow.",
        )
    }

    @ExceptionHandler(SentenceEvaluationUpstreamException::class)
    fun handleSentenceEvaluationUpstream(
        ex: SentenceEvaluationUpstreamException,
    ): ResponseEntity<ErrorResponse> {
        logger.error("Sentence evaluation upstream failure", ex)
        return buildResponse(
            HttpStatus.BAD_GATEWAY,
            "The sentence evaluation service is currently unavailable. Please try again later.",
        )
    }

    @ExceptionHandler(SentenceEvaluationMisconfiguredException::class)
    fun handleSentenceEvaluationMisconfigured(
        ex: SentenceEvaluationMisconfiguredException,
    ): ResponseEntity<ErrorResponse> {
        logger.error("Sentence evaluation is not configured: {}", ex.message)
        return buildResponse(
            HttpStatus.BAD_GATEWAY,
            "Sentence evaluation is not configured on this server.",
        )
    }

    @ExceptionHandler(EssayEvaluationQuotaExceededException::class)
    fun handleEssayEvaluationQuota(
        ex: EssayEvaluationQuotaExceededException,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Essay evaluation quota exceeded: {}", ex.message)
        return buildResponse(
            HttpStatus.TOO_MANY_REQUESTS,
            "Daily limit of ${ex.dailyLimit} essay evaluations reached. Try again tomorrow.",
        )
    }

    @ExceptionHandler(EssayEvaluationUpstreamException::class)
    fun handleEssayEvaluationUpstream(
        ex: EssayEvaluationUpstreamException,
    ): ResponseEntity<ErrorResponse> {
        logger.error("Essay evaluation upstream failure", ex)
        return buildResponse(
            HttpStatus.BAD_GATEWAY,
            "The essay evaluation service is currently unavailable. Please try again later.",
        )
    }

    @ExceptionHandler(EssayEvaluationMisconfiguredException::class)
    fun handleEssayEvaluationMisconfigured(
        ex: EssayEvaluationMisconfiguredException,
    ): ResponseEntity<ErrorResponse> {
        logger.error("Essay evaluation is not configured: {}", ex.message)
        return buildResponse(
            HttpStatus.BAD_GATEWAY,
            "Essay evaluation is not configured on this server.",
        )
    }

    @ExceptionHandler(AskSessionInvalidException::class)
    fun handleAskSessionInvalid(ex: AskSessionInvalidException): ResponseEntity<ErrorResponse> {
        val (status, code) = when (ex.reason) {
            AskSessionInvalidException.Reason.TURNSTILE_FAILED -> HttpStatus.FORBIDDEN to AskErrorCode.TURNSTILE_FAILED
            AskSessionInvalidException.Reason.SESSION_EXPIRED -> HttpStatus.UNAUTHORIZED to AskErrorCode.SESSION_EXPIRED
        }
        logger.warn("Ask session refused: {}", ex.reason)
        return buildResponse(status, ex.reason.text, code)
    }

    @ExceptionHandler(AskRateLimitedException::class)
    fun handleAskRateLimited(ex: AskRateLimitedException): ResponseEntity<ErrorResponse> {
        logger.warn("Ask rate limit reached: {}", ex.message)
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, AskErrorEvent.from(ex).message, AskErrorCode.RATE_LIMITED)
    }

    @ExceptionHandler(AskConversationLimitException::class)
    fun handleAskConversationLimit(ex: AskConversationLimitException): ResponseEntity<ErrorResponse> {
        logger.warn("Ask conversation limit reached: {}", ex.message)
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, AskErrorEvent.from(ex).message, AskErrorCode.CONVERSATION_LIMIT)
    }

    @ExceptionHandler(AskBusyException::class)
    fun handleAskBusy(ex: AskBusyException): ResponseEntity<ErrorResponse> {
        logger.warn("Ask request refused: {}", ex.message)
        return buildResponse(
            HttpStatus.SERVICE_UNAVAILABLE,
            "The assistant is busy right now. Please try again in a moment.",
            AskErrorCode.BUSY,
        )
    }

    @ExceptionHandler(TurnstileVerificationException::class)
    fun handleTurnstileVerification(ex: TurnstileVerificationException): ResponseEntity<ErrorResponse> {
        logger.error("Turnstile verification failed", ex)
        return buildResponse(
            HttpStatus.BAD_GATEWAY,
            "The bot check couldn't be completed right now. Please try again in a moment.",
            AskErrorCode.UPSTREAM,
        )
    }

    /**
     * A chat stream reached its timeout ([AskEventStream]). Its response has already started as an
     * event stream, so nothing is written: a JSON error body would only be appended to the stream.
     * The visitor's browser sees the stream end without `done`.
     */
    @ExceptionHandler(AsyncRequestTimeoutException::class)
    fun handleAsyncRequestTimeout() {
        logger.info("Streaming response timed out")
    }

    /**
     * The visitor left while a chat stream was open ([AskEventStream]), e.g. by closing the tab.
     * That's a normal ending, not an error, and nothing can be written to the closed connection.
     */
    @ExceptionHandler(AsyncRequestNotUsableException::class)
    fun handleAsyncRequestNotUsable() {
        logger.info("Streaming response ended, the client disconnected")
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        logger.warn("Malformed request body: {}", ex.message)
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed or missing request body.")
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        logger.warn("Parameter type mismatch: name={} required={}", ex.name, ex.requiredType?.simpleName)
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid value for parameter '${ex.name}'.")
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedMediaType(ex: HttpMediaTypeNotSupportedException): ResponseEntity<ErrorResponse> {
        logger.warn("Unsupported media type: {}", ex.message)
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type for this endpoint.")
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(ex: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> {
        logger.warn("Method not supported: {}", ex.message)
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported for this endpoint.")
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(ex: NoResourceFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("No handler for request: {}", ex.message)
        return buildResponse(HttpStatus.NOT_FOUND, "The requested resource was not found.")
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("An unexpected error occurred", ex)
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred")
    }

    /**
     * The error body, always as JSON. The content type is set explicitly because otherwise Spring
     * picks it from the request's `Accept` header: a request for `text/event-stream` (the chat
     * stream) would then get no body at all, since no converter writes JSON as an event stream.
     */
    private fun buildResponse(
        status: HttpStatus,
        message: String?,
        code: AskErrorCode? = null,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(
            ErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = message ?: "No details available",
                code = code?.name,
            ),
        )
}