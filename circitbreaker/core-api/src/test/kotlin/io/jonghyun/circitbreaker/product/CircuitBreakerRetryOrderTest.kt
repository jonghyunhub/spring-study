package io.jonghyun.circitbreaker.product

import com.ninjasquad.springmockk.MockkBean
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.io.IOException

/**
 * @Retry와 @CircuitBreaker를 같은 메서드에 붙였을 때의 실행 순서를 확인하는 테스트.
 *
 * aspect order (값이 작을수록 바깥):
 *   RetryAspect          = Ordered.LOWEST_PRECEDENCE - 5
 *   CircuitBreakerAspect = Ordered.LOWEST_PRECEDENCE - 4
 *
 * 즉 Retry가 바깥, CircuitBreaker가 안쪽이다.
 * 그런데 fallback 처리(FallbackExecutor)는 각 aspect가 '자기 레이어 안에서' 수행한다.
 * 따라서 CB의 fallback이 예외를 삼키고 정상 값을 반환하면, 바깥의 Retry에게는 성공으로 보인다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class CircuitBreakerRetryOrderTest(
    @Autowired private val productService: ProductService,
    @Autowired private val circuitBreakerRegistry: CircuitBreakerRegistry,
) {

    @MockkBean
    private lateinit var stubServiceClient: StubServiceClient

    private val circuitBreaker get() = circuitBreakerRegistry.circuitBreaker(CB_NAME)

    @BeforeEach
    fun setUp() {
        circuitBreaker.reset()
        clearMocks(stubServiceClient)
        every { stubServiceClient.getProduct(any()) } throws IOException("stub-service down")
    }

    @Test
    @DisplayName("S7. CircuitBreaker의 fallback이 있으면 Retry가 재시도하지 않는다")
    fun fallbackSwallowsExceptionBeforeRetrySeesIt() {
        val response = productService.getProductWithRetryAndFallback(1L)

        log("S7")
        assertThat(response.status).isEqualTo("FALLBACK")
        // max-attempts = 3 이지만 실제 호출은 1회뿐이다.
        // 안쪽 CB 레이어에서 fallback이 예외를 값으로 바꿔버려 바깥 Retry가 실패를 보지 못한다.
        verify(exactly = 1) { stubServiceClient.getProduct(any()) }
        assertThat(circuitBreaker.metrics.numberOfFailedCalls).isEqualTo(1)
    }

    @Test
    @DisplayName("S8. fallback이 없으면 Retry가 동작하고, 재시도 횟수만큼 서킷 표본이 소모된다")
    fun withoutFallbackRetryRunsAndConsumesCircuitSamples() {
        assertThatThrownBy { productService.getProductWithRetryOnly(1L) }
            .isInstanceOf(IOException::class.java)

        log("S8")
        // 예외가 CB 레이어를 그대로 통과해 바깥 Retry까지 올라가므로 max-attempts만큼 재시도된다.
        verify(exactly = 3) { stubServiceClient.getProduct(any()) }
        // API 호출은 1건인데 서킷 통계에는 3건이 쌓인다.
        // sliding-window-size를 잡을 때 이 배수를 감안하지 않으면 예상보다 빨리 열린다.
        assertThat(circuitBreaker.metrics.numberOfFailedCalls).isEqualTo(3)
        assertThat(circuitBreaker.metrics.numberOfBufferedCalls).isEqualTo(3)
        // minimum-number-of-calls = 5 이므로 아직 판정 전이다.
        assertThat(circuitBreaker.state).isEqualTo(State.CLOSED)
    }

    private fun log(scenario: String) {
        with(circuitBreaker.metrics) {
            println(
                "[$scenario] state=${circuitBreaker.state} buffered=$numberOfBufferedCalls " +
                    "success=$numberOfSuccessfulCalls failed=$numberOfFailedCalls " +
                    "failureRate=$failureRate",
            )
        }
    }

    companion object {
        private const val CB_NAME = "stub-service"
    }
}
