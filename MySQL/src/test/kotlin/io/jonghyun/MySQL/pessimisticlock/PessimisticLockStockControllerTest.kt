package io.jonghyun.MySQL.pessimisticlock

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.jonghyun.MySQL.common.IntegrationTest
import io.jonghyun.MySQL.domain.Stock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PessimisticLockStockControllerTest(
    private val testRestTemplate: TestRestTemplate,
    private val stockRepository: PessimisticLockStockRepository
) : IntegrationTest() {

    companion object {
        private val objectMapper: ObjectMapper = jacksonObjectMapper()
    }

    private fun prettyJson(json: String): String {
        return objectMapper.writerWithDefaultPrettyPrinter()
            .writeValueAsString(objectMapper.readTree(json))
    }

    @BeforeEach
    fun setUp() {
        stockRepository.deleteAll()
        stockRepository.saveAll(
            listOf(
                Stock(productId = 1, quantity = 100),
                Stock(productId = 2, quantity = 100),
                Stock(productId = 3, quantity = 50),
                Stock(productId = 4, quantity = 200),
                Stock(productId = 10, quantity = 150),
                Stock(productId = 20, quantity = 80)
            )
        )
    }

    @Test
    @DisplayName("Record Lock (PK) - 동일 레코드 락 시 대기 발생")
    fun testRecordLockByPrimaryKey()  {
        // given
        val stock = stockRepository.findAll().first()
        val stockId = stock.id!!
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        val durations = mutableListOf<Long>()

        // when: Thread-1이 3초 락 유지
        executor.submit {
            try {
                val start = System.currentTimeMillis()
                val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
                val request = HttpEntity(LockByIdRequest(id = stockId, holdLockSeconds = 3), headers)
                testRestTemplate.postForEntity("/pessimistic-lock/lock-by-primary-key", request, String::class.java)
                durations.add(System.currentTimeMillis() - start)
            } finally {
                latch.countDown()
            }
        }

        Thread.sleep(100) // Thread-1이 먼저 락 획득하도록

        // when: Thread-2가 동일 레코드 락 시도
        executor.submit {
            try {
                val start = System.currentTimeMillis()
                val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
                val request = HttpEntity(LockByIdRequest(id = stockId), headers)
                testRestTemplate.postForEntity("/pessimistic-lock/lock-by-primary-key", request, String::class.java)
                durations.add(System.currentTimeMillis() - start)
            } finally {
                latch.countDown()
            }
        }

        // then
        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(durations[0]).isLessThan(3500) // Thread-1: ~3초
        assertThat(durations[1]).isGreaterThan(2900) // Thread-2: 3초 이상 대기
    }

    @Test
    @DisplayName("Phase 2-2: Record Lock (Unique Index) - 정상 동작 확인")
    fun testRecordLockByUniqueIndex() {
        // given & when
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val request = HttpEntity(LockByProductIdRequest(productId = 1), headers)
        val response = testRestTemplate.postForEntity("/pessimistic-lock/lock-by-unique-index", request, String::class.java)

        // then: Unique Index로 조회해도 Record Lock 동작 확인
        val actualJson = response.body!!
        val stock1 = stockRepository.findAll().find { it.productId == 1L }!!
        val expectedJson = """
        {
          "success": true,
          "message": "Lock acquired by unique index: productId=1",
          "stock": {"id": ${stock1.id}, "productId": 1, "quantity": 100, "version": 0}
        }
        """
        JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.LENIENT)
    }

    @Test
    @DisplayName("Phase 3: Gap Lock - 존재하지 않는 범위 조회 시 INSERT 차단")
    fun testGapLock() {
        // given: 현재 id = 1,2,3,4,5,6 존재
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        val results = mutableListOf<String>()

        // when: Thread-1이 id 7~9 범위 락 (존재하지 않는 범위 -> Gap Lock)
        executor.submit {
            try {
                val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
                val request = HttpEntity(LockByRangeRequest(startId = 7, endId = 9, holdLockSeconds = 3), headers)
                val response = testRestTemplate.postForEntity("/pessimistic-lock/lock-by-range", request, String::class.java)

                val actualJson = response.body!!
                val expectedJson = """
                {
                  "success": true,
                  "message": "Range lock: 7~9, found 0 records",
                  "stocks": []
                }
                """
                JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.LENIENT)
                results.add("Thread-1: Gap Lock acquired (0 records)")
            } finally {
                latch.countDown()
            }
        }

        Thread.sleep(100)

        // when: Thread-2가 id=7 영역에 INSERT 시도 (실제로는 테스트에서 INSERT는 어려우므로 락 확인으로 대체)
        // 실제 Gap Lock 확인은 MySQL에서 performance_schema로 확인
        executor.submit {
            try {
                val start = System.currentTimeMillis()
                // 동일한 범위를 다시 락 시도하면 대기해야 함
                val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
                val request = HttpEntity(LockByRangeRequest(startId = 7, endId = 9), headers)
                testRestTemplate.postForEntity("/pessimistic-lock/lock-by-range", request, String::class.java)
                val duration = System.currentTimeMillis() - start
                results.add("Thread-2: Waited ${duration}ms for Gap Lock")

                // Gap Lock으로 인해 약 3초 대기해야 함
                assertThat(duration).isGreaterThan(2900)
            } finally {
                latch.countDown()
            }
        }

        // then
        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        println("Gap Lock 테스트 결과:")
        results.forEach { println("  $it") }
    }

    @Test
    @DisplayName("Phase 4: Next Key Lock - 범위 조회 시 Record + Gap Lock")
    fun testNextKeyLock() {
        // given: id = 1,2,3,4,5,6 존재
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)

        // when: Thread-1이 id >= 3 조회 (id 3,4,5,6 + 6 이후 Gap)
        executor.submit {
            try {
                val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
                val request = HttpEntity(LockByRangeRequest(startId = 3, endId = Long.MAX_VALUE, holdLockSeconds = 3), headers)
                testRestTemplate.postForEntity("/pessimistic-lock/lock-by-range", request, String::class.java)
            } finally {
                latch.countDown()
            }
        }

        Thread.sleep(100)

        // when: Thread-2가 id=4 업데이트 시도 (Record Lock에 걸림)
        executor.submit {
            try {
                val start = System.currentTimeMillis()
                val stock4 = stockRepository.findAll().find { it.productId == 4L }!!
                val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
                val request = HttpEntity(LockByIdRequest(id = stock4.id!!), headers)
                testRestTemplate.postForEntity("/pessimistic-lock/lock-by-primary-key", request, String::class.java)
                val duration = System.currentTimeMillis() - start

                // Record Lock으로 약 3초 대기
                assertThat(duration).isGreaterThan(2900)
                println("Next Key Lock: id=4 업데이트 시도, ${duration}ms 대기")
            } finally {
                latch.countDown()
            }
        }

        // then
        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()
    }

    @Test
    @DisplayName("Phase 5: 인덱스 없는 컬럼 조회 - 전체 테이블 락")
    fun testFullTableLockWithoutIndex() {
        // given: quantity 컬럼에 인덱스 없음
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)

        // when: Thread-1이 quantity=100 조회 (인덱스 없어서 전체 테이블 스캔 -> 전체 락)
        executor.submit {
            try {
                val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
                val request = HttpEntity(LockByQuantityRequest(quantity = 100, holdLockSeconds = 3), headers)
                val response = testRestTemplate.postForEntity("/pessimistic-lock/lock-by-quantity", request, String::class.java)

                val actualJson = response.body!!
                // quantity=100인 레코드는 2개 (productId=1,2)
                val expectedStocks = stockRepository.findAll().filter { it.quantity == 100 }.sortedBy { it.productId }
                val expectedJson = """
                {
                  "success": true,
                  "message": "Lock by quantity=100, found 2 records",
                  "stocks": [
                    {"id": ${expectedStocks[0].id}, "productId": 1, "quantity": 100, "version": 0},
                    {"id": ${expectedStocks[1].id}, "productId": 2, "quantity": 100, "version": 0}
                  ]
                }
                """
                JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.LENIENT)
            } finally {
                latch.countDown()
            }
        }

        Thread.sleep(100)

        // when: Thread-2가 quantity=50인 다른 레코드 업데이트 시도
        // 인덱스가 없으면 전체 테이블 스캔으로 모든 레코드에 락이 걸리므로 대기해야 함
        executor.submit {
            try {
                val start = System.currentTimeMillis()
                val stock3 = stockRepository.findAll().find { it.productId == 3L }!! // quantity=50
                val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
                val request = HttpEntity(LockByIdRequest(id = stock3.id!!), headers)
                testRestTemplate.postForEntity("/pessimistic-lock/lock-by-primary-key", request, String::class.java)
                val duration = System.currentTimeMillis() - start

                // 전체 테이블 락으로 인해 약 3초 대기
                assertThat(duration).isGreaterThan(2900)
                println("인덱스 없는 컬럼 조회: quantity=50 레코드도 ${duration}ms 대기 (전체 테이블 락)")
            } finally {
                latch.countDown()
            }
        }

        // then
        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()
    }

    @Test
    @DisplayName("Phase 6: 재고 차감 - 락 없이 100번 동시 차감 (Lost Update)")
    fun testDecreaseStockWithoutLock() {
        // given
        val stock = stockRepository.findAll().first()
        val stockId = stock.id!!
        val threadCount = 100
        val executor = Executors.newFixedThreadPool(32)
        val latch = CountDownLatch(threadCount)

        // when: 락 없이 100번 차감
        repeat(threadCount) {
            executor.submit {
                try {
                    val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
                    val request = HttpEntity(DecreaseStockRequest(id = stockId, amount = 1), headers)
                    testRestTemplate.postForEntity(
                        "/pessimistic-lock/decrease-stock-without-lock",
                        request,
                        String::class.java
                    )
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // then: Lost Update 발생으로 0이 아님
        val finalStock = stockRepository.findById(stockId).get()
        assertThat(finalStock.quantity).isNotEqualTo(0)
        println("최종 재고 (락 없음): ${finalStock.quantity} (예상: 0, 실제: Lost Update 발생)")
    }

    @Test
    @DisplayName("Phase 6: 재고 차감 - Pessimistic Lock으로 100번 동시 차감 성공")
    fun testDecreaseStockWithLock() {
        // given
        val stock = stockRepository.findAll().first()
        val stockId = stock.id!!
        val threadCount = 100
        val executor = Executors.newFixedThreadPool(32)
        val latch = CountDownLatch(threadCount)

        // when: Pessimistic Lock으로 100번 차감
        repeat(threadCount) {
            executor.submit {
                try {
                    val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
                    val request = HttpEntity(DecreaseStockRequest(id = stockId, amount = 1), headers)
                    testRestTemplate.postForEntity(
                        "/pessimistic-lock/decrease-stock",
                        request,
                        String::class.java
                    )
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // then: 정확히 0이 되어야 함
        val finalStock = stockRepository.findById(stockId).get()
        assertThat(finalStock.quantity).isEqualTo(0)
        println("최종 재고 (락 사용): ${finalStock.quantity} (예상: 0)")
    }
}
