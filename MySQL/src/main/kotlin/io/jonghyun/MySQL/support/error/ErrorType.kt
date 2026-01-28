package io.jonghyun.MySQL.support.error

import org.springframework.boot.logging.LogLevel
import org.springframework.http.HttpStatus

enum class ErrorType(val status: HttpStatus, val code: ErrorCode, val message: String, val logLevel: LogLevel) {
    DEFAULT_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCode.E500,
        "An unexpected error has occurred.", LogLevel.ERROR,
    ),
    LOCK_KEY_GENERATION_FAILED(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCode.E500,
        "Failed to generate lock key from method parameters.",
        LogLevel.ERROR,
    ),
}
