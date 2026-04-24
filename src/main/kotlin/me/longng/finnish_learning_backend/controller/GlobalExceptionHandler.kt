package me.longng.finnish_learning_backend.controller

import me.longng.finnish_learning_backend.service.CardNotFoundException
import me.longng.finnish_learning_backend.service.TopicNotFoundException
import me.longng.finnish_learning_backend.storage.ImageStorageException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.support.MissingServletRequestPartException

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

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("An unexpected error occurred", ex)
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred")
    }

    private fun buildResponse(status: HttpStatus, message: String?): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(
            ErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = message ?: "No details available",
            ),
        )
}
