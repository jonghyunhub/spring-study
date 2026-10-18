package io.jonghyun.circitbreaker.product

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * SingleFlight 동시성 테스트.
 * 같은 상품을 동시에 조회했을 때 DB(ProductRepository) 조회가 몇 번 일어나는지 확인한다.
 * Redis 는 Map 기반 fake 로 대체해서 set 한 값이 이후 get 에 보이도록 한다.
 */
class SingleFlightServiceTest {

    private val redisTemplate = mockk<RedisTemplate<String, ProductDto>>()
    private val valueOperations = mockk<ValueOperations<String, ProductDto>>()
    private val productRepository = mockk<ProductRepository>()
    private val fakeRedis = ConcurrentHashMap<String, ProductDto>()
    private val dbCallCount = AtomicInteger()

    @BeforeEach
    fun setUp() {
        every { redisTemplate.opsForValue() } returns valueOperations
        every { valueOperations.get(any()) } answers { fakeRedis[firstArg()] }
        every { valueOperations.set(any(), any(), any<Duration>()) } answers { fakeRedis[firstArg()] = secondArg() }
    }

    @Test
    @DisplayName("같은 상품을 동시에 100번 조회해도 DB 조회는 1번만 일어난다")
    fun concurrentRequestsHitDbOnce() {
        val service = SingleFlightService(redisTemplate, productRepository, SingleFlight())
        every { productRepository.findById(1L) } answers {
            dbCallCount.incrementAndGet()
            Thread.sleep(DB_LATENCY_MS) // 모든 요청이 leader 처리 중에 도착하도록 DB 지연을 흉내낸다
            Optional.of(Product(1L, "상품1", "ON_SALE"))
        }

        val result = runConcurrently(THREAD_COUNT) { service.getProductsWithSingleFlight(1L) }
        printMetrics("SingleFlight 성공", result)

        assertThat(result.success).isEqualTo(THREAD_COUNT)
        verify(exactly = 1) { productRepository.findById(1L) }
    }

    @Test
    @DisplayName("leader 의 DB 조회가 실패하면 기다리던 follower 들도 함께 실패한다")
    fun leaderFailurePropagatesToFollowers() {
        val service = SingleFlightService(redisTemplate, productRepository, SingleFlight())
        every { productRepository.findById(1L) } answers {
            dbCallCount.incrementAndGet()
            Thread.sleep(DB_LATENCY_MS)
            Optional.empty()
        }

        val result = runConcurrently(THREAD_COUNT) { service.getProductsWithSingleFlight(1L) }
        printMetrics("SingleFlight 실패 공유", result)

        assertThat(result.failure).isEqualTo(THREAD_COUNT)
        verify(exactly = 1) { productRepository.findById(1L) }
    }

    @Test
    @DisplayName("[비교군] SingleFlight 가 없으면 동시 요청 수만큼 DB 조회가 일어난다")
    fun withoutSingleFlightHitsDbPerRequest() {
        val service = SingleFlightService(redisTemplate, productRepository, SingleFlight())
        every { productRepository.findById(1L) } answers {
            dbCallCount.incrementAndGet()
            Thread.sleep(DB_LATENCY_MS)
            Optional.of(Product(1L, "상품1", "ON_SALE"))
        }

        val result = runConcurrently(THREAD_COUNT) { service.getProductsWithOutSingleFlight(1L) }
        printMetrics("[비교군] SingleFlight 없음", result)

        verify(exactly = THREAD_COUNT) { productRepository.findById(1L) }
    }

    /** threadCount 개의 요청을 동시에 실행하고, 성공/실패 횟수를 센다. */
    private fun runConcurrently(threadCount: Int, task: () -> Unit): Result {
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val startTime = System.currentTimeMillis()
        val success = AtomicInteger()
        val failure = AtomicInteger()

        repeat(threadCount) {
            executor.submit {
                try {
                    task()
                    success.incrementAndGet()
                } catch (_: Throwable) {
                    failure.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()
        return Result(success.get(), failure.get(), System.currentTimeMillis() - startTime)
    }

    private fun printMetrics(scenario: String, result: Result) {
        println(
            """
            |===== $scenario =====
            |요청 수     : $THREAD_COUNT
            |성공 / 실패 : ${result.success} / ${result.failure}
            |DB 조회 수  : ${dbCallCount.get()}
            |총 소요 시간 : ${result.elapsedMs}ms
            """.trimMargin(),
        )
    }

    private data class Result(val success: Int, val failure: Int, val elapsedMs: Long)

    companion object {
        private const val THREAD_COUNT = 100
        private const val DB_LATENCY_MS = 300L
    }
}
