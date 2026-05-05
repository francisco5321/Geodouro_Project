package com.example.geodouro_backend.config

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException

@ControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceededException(
        exception: MaxUploadSizeExceededException
    ): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(
                ApiErrorResponse(
                    error = "payload_too_large",
                    message = exception.message ?: "Uploaded file exceeds the allowed size."
                )
            )
    }

    @ExceptionHandler(MultipartException::class)
    fun handleMultipartException(
        exception: MultipartException
    ): ResponseEntity<ApiErrorResponse> {
        val status = if (exception.cause is MaxUploadSizeExceededException) {
            HttpStatus.PAYLOAD_TOO_LARGE
        } else {
            HttpStatus.BAD_REQUEST
        }

        val message = if (status == HttpStatus.PAYLOAD_TOO_LARGE) {
            "Uploaded file exceeds the allowed size."
        } else {
            exception.message ?: "Invalid multipart request."
        }

        return ResponseEntity
            .status(status)
            .body(ApiErrorResponse(error = "multipart_error", message = message))
    }
}

data class ApiErrorResponse(
    val error: String,
    val message: String
)
