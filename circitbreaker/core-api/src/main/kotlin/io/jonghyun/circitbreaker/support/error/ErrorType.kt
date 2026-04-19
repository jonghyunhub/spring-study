package io.jonghyun.circitbreaker.support.error

import org.springframework.boot.logging.LogLevel
import org.springframework.http.HttpStatus

enum class ErrorType(val status: HttpStatus, val code: ErrorCode, val message: String, val logLevel: LogLevel) {
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "An unexpected error has occurred.", LogLevel.ERROR),

    // Circuit Breaker: OPEN 상태에서 요청이 차단됨 (빠른 실패)
    CIRCUIT_BREAKER_OPEN(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.E503, "Service is temporarily unavailable. Circuit breaker is open.", LogLevel.WARN),

    // 외부 서비스가 실제로 오류 응답을 반환하거나 호출 자체가 실패한 경우
    EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, ErrorCode.E502, "External service is unavailable.", LogLevel.ERROR),
}
