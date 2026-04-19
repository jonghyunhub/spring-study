package io.jonghyun.circitbreaker.product

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.jonghyun.circitbreaker.support.error.CoreException
import io.jonghyun.circitbreaker.support.error.ErrorType
import org.springframework.stereotype.Service

@Service
class ProductService(
    private val stubServiceClient: StubServiceClient,
) {

    // "stub-service"는 application.yml의 resilience4j.circuitbreaker.instances 키와 일치해야 합니다.
    @CircuitBreaker(name = "stub-service", fallbackMethod = "getProductFallback")
    fun getProduct(id: Long): ProductResponse {
        return stubServiceClient.getProduct(id)
    }

    // CB가 OPEN 상태일 때 — 외부 서비스를 호출하지 않고 즉시 차단 (빠른 실패)
    private fun getProductFallback(id: Long, e: CallNotPermittedException): ProductResponse {
        throw CoreException(ErrorType.CIRCUIT_BREAKER_OPEN)
    }

    // 외부 서비스가 실제로 실패했을 때 (500 응답, 타임아웃, 연결 거부 등)
    private fun getProductFallback(id: Long, e: Exception): ProductResponse {
        throw CoreException(ErrorType.EXTERNAL_SERVICE_ERROR)
    }
}
