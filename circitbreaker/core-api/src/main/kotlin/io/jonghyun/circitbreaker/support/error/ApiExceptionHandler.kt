package io.jonghyun.circitbreaker.support.error

import io.jonghyun.circitbreaker.support.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.logging.LogLevel
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(CoreException::class)
    fun handleCoreException(e: CoreException): ResponseEntity<ApiResponse<Nothing>> {
        when (e.errorType.logLevel) {
            LogLevel.WARN -> log.warn(e.message, e)
            LogLevel.ERROR -> log.error(e.message, e)
            else -> log.info(e.message, e)
        }
        return ResponseEntity
            .status(e.errorType.status)
            .body(ApiResponse.error(e.errorType, e.data))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error(e.message, e)
        return ResponseEntity
            .status(ErrorType.DEFAULT_ERROR.status)
            .body(ApiResponse.error(ErrorType.DEFAULT_ERROR))
    }
}
