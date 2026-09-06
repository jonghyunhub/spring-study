package io.jonghyun.circitbreaker.product

import com.ninjasquad.springmockk.MockkBean
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.jonghyun.circitbreaker.support.error.CoreException
import io.jonghyun.circitbreaker.support.error.ErrorType
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
 * Resilience4j Circuit Breaker가 실제로 어떤 순서로 동작하는지 확인하는 테스트.
 *
 * 호출 체인 (동기 메서드 기준):
 *   CircuitBreakerAspect.circuitBreakerAroundAdvice
 *     -> FallbackExecutor.execute
 *       -> DefaultFallbackDecorator.decorate      // catch (Throwable) -> fallback
 *         -> CircuitBreaker.decorateCheckedSupplier
 *              acquirePermission()               // OPEN이면 여기서 CallNotPermittedException
 *              proceedingJoinPoint.proceed()     // 원본 메서드 (여기서는 Feign 호출)
 *              onResult() / onError()            // 통계 기록 + 상태 전이 판단
 *
 * 반드시 스프링 컨텍스트에서 ProductService를 주입받아야 한다.
 * 직접 생성한 인스턴스는 AOP 프록시가 아니라 @CircuitBreaker가 동작하지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class CircuitBreakerBehaviorTest(
    @Autowired private val productService: ProductService,
    @Autowired private val circuitBreakerRegistry: CircuitBreakerRegistry,
) {

    @MockkBean
    private lateinit var stubServiceClient: StubServiceClient

    private val circuitBreaker get() = circuitBreakerRegistry.circuitBreaker(CB_NAME)

    @BeforeEach
    fun setUp() {
        // reset()은 새 ClosedState를 넣어 상태와 통계를 함께 초기화한다.
        // 스프링 컨텍스트가 테스트 간 캐싱되므로 이 초기화가 없으면 앞 테스트의 통계가 넘어온다.
        circuitBreaker.reset()
        clearMocks(stubServiceClient)
    }

    @Test
    @DisplayName("S1. minimumNumberOfCalls 미만이면 100% 실패해도 회로는 열리지 않는다")
    fun belowMinimumNumberOfCallsStaysClosed() {
        givenClientFails()

        repeat(4) { productService.getProductOrCached(1L) } // minimum-number-of-calls = 5

        log("S1")
        // getFailureRate()는 표본이 minimum 미만이면 -1을 반환하고, 그 경우 임계값 비교 자체를 건너뛴다.
        assertThat(circuitBreaker.metrics.failureRate).isEqualTo(-1.0f)
        assertThat(circuitBreaker.metrics.numberOfFailedCalls).isEqualTo(4)
        assertThat(circuitBreaker.state).isEqualTo(State.CLOSED)
    }

    @Test
    @DisplayName("S2. 표본이 채워지고 실패율이 임계를 넘으면 CLOSED -> OPEN")
    fun exceedingFailureRateOpensCircuit() {
        givenClientFails()

        repeat(4) { productService.getProductOrCached(1L) }
        assertThat(circuitBreaker.state).isEqualTo(State.CLOSED)

        productService.getProductOrCached(1L) // 5번째 호출에서 표본이 채워진다

        log("S2")
        assertThat(circuitBreaker.metrics.failureRate).isEqualTo(100.0f)
        assertThat(circuitBreaker.state).isEqualTo(State.OPEN)
    }

    @Test
    @DisplayName("S3. OPEN 상태에서는 원본 메서드가 실행조차 되지 않는다")
    fun openStateDoesNotInvokeOriginalMethod() {
        givenClientFails()
        repeat(5) { productService.getProductOrCached(1L) }
        assertThat(circuitBreaker.state).isEqualTo(State.OPEN)

        // 지금까지의 호출 기록만 지우고 스텁(예외 발생 설정)은 유지한다.
        clearMocks(stubServiceClient, answers = false)

        // acquirePermission()이 proceed()보다 먼저라서 Feign 호출은 시도되지 않는다.
        assertThatThrownBy { productService.getProduct(1L) }
            .isInstanceOf(CoreException::class.java)
            .extracting("errorType")
            .isEqualTo(ErrorType.CIRCUIT_BREAKER_OPEN)

        log("S3")
        verify(exactly = 0) { stubServiceClient.getProduct(any()) }
        assertThat(circuitBreaker.metrics.numberOfNotPermittedCalls).isEqualTo(1L)
    }

    @Test
    @DisplayName("S4. fallback이 호출됐다고 회로가 열린 것은 아니다")
    fun fallbackIsInvokedWhileCircuitStaysClosed() {
        givenClientFails()

        val response = productService.getProductOrCached(1L)

        log("S4")
        // fallback을 부르는 주체는 DefaultFallbackDecorator의 catch(Throwable) 하나뿐이고,
        // 이 데코레이터는 서킷 로직보다 바깥에 있어 회로 상태를 전혀 보지 않는다.
        assertThat(response.status).isEqualTo("FALLBACK") // fallback은 확실히 탔다
        assertThat(circuitBreaker.state).isEqualTo(State.CLOSED) // 그런데 회로는 닫혀 있다
        assertThat(circuitBreaker.metrics.numberOfFailedCalls).isEqualTo(1)
    }

    @Test
    @DisplayName("S5. record-exceptions에 없는 예외는 무시가 아니라 '성공'으로 기록된다")
    fun exceptionOutsideRecordListIsCountedAsSuccess() {
        // record-exceptions는 IOException, FeignException 두 개뿐이다.
        every { stubServiceClient.getProduct(any()) } throws IllegalStateException("not recorded")

        repeat(10) { productService.getProductOrCached(1L) }

        log("S5")
        // handleThrowable(): recordExceptionPredicate가 false면 onSuccess()로 넘어간다.
        assertThat(circuitBreaker.metrics.numberOfSuccessfulCalls).isEqualTo(10)
        assertThat(circuitBreaker.metrics.numberOfFailedCalls).isEqualTo(0)
        assertThat(circuitBreaker.metrics.failureRate).isEqualTo(0.0f)
        assertThat(circuitBreaker.state).isEqualTo(State.CLOSED)
        // 10번 모두 fallback은 탔다. 즉 "fallback 폭주 + 실패율 0%"가 동시에 성립한다.
        verify(exactly = 10) { stubServiceClient.getProduct(any()) }
    }

    @Test
    @DisplayName("S6. OPEN -> HALF_OPEN -> CLOSED, 판정 기준은 minimum(5)이 아니라 permitted(3)")
    fun halfOpenRecoversWithPermittedCalls() {
        givenClientOpensCircuit()

        Thread.sleep(WAIT_IN_OPEN_MILLIS + 100)
        assertThat(circuitBreaker.state).isEqualTo(State.OPEN) // 시간만 지났을 뿐 아직 OPEN

        givenClientSucceeds()

        // OPEN -> HALF_OPEN 전이는 스케줄러가 아니라 '다음 요청 스레드'가 수행한다.
        productService.getProductOrCached(1L)
        assertThat(circuitBreaker.state).isEqualTo(State.HALF_OPEN)

        productService.getProductOrCached(1L)
        assertThat(circuitBreaker.state).isEqualTo(State.HALF_OPEN)

        // HALF_OPEN의 metrics는 forHalfOpen(permitted, config)로 만들어지고
        // 그 안에서 minimumNumberOfCalls = min(설정값 5, permitted 3) = 3으로 clamp된다.
        // 그래서 5건이 아니라 3건만 채워도 판정이 끝난다.
        productService.getProductOrCached(1L)

        log("S6")
        assertThat(circuitBreaker.state).isEqualTo(State.CLOSED)
    }

    @Test
    @DisplayName("S6-b. HALF_OPEN 표본에서 실패율이 임계를 넘으면 다시 OPEN")
    fun halfOpenReopensOnFailure() {
        givenClientOpensCircuit()
        Thread.sleep(WAIT_IN_OPEN_MILLIS + 100)

        givenClientFails()
        productService.getProductOrCached(1L)
        assertThat(circuitBreaker.state).isEqualTo(State.HALF_OPEN)
        productService.getProductOrCached(1L)

        givenClientSucceeds()
        productService.getProductOrCached(1L) // 3건 중 2건 실패 = 66% > 50%

        log("S6-b")
        assertThat(circuitBreaker.state).isEqualTo(State.OPEN)
    }

    private fun givenClientFails() {
        // IOException은 application.yml의 record-exceptions에 포함되어 실패로 카운트된다.
        every { stubServiceClient.getProduct(any()) } throws IOException("stub-service down")
    }

    private fun givenClientSucceeds() {
        every { stubServiceClient.getProduct(any()) } returns
            ProductResponse(id = 1L, name = "Product 1", status = "OK")
    }

    private fun givenClientOpensCircuit() {
        givenClientFails()
        repeat(5) { productService.getProductOrCached(1L) }
        check(circuitBreaker.state == State.OPEN) { "사전 조건 실패: 회로가 열리지 않았다" }
    }

    private fun log(scenario: String) {
        with(circuitBreaker.metrics) {
            println(
                "[$scenario] state=${circuitBreaker.state} buffered=$numberOfBufferedCalls " +
                    "success=$numberOfSuccessfulCalls failed=$numberOfFailedCalls " +
                    "notPermitted=$numberOfNotPermittedCalls failureRate=$failureRate",
            )
        }
    }

    companion object {
        private const val CB_NAME = "stub-service"
        private const val WAIT_IN_OPEN_MILLIS = 500L // application-test.yml의 wait-duration-in-open-state
    }
}
