package io.jonghyun.MySQL.lock

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class StockControllerTest(
    private val testRestTemplate: TestRestTemplate,
    private val stockRepository: StockRepository
) : IntegrationTest() {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(StockControllerTest::class.java)
        const val PRODUCT_ID = 1L
        const val INITIAL_QUANTITY = 10
    }

    @BeforeEach
    fun setUp() {
        stockRepository.deleteAll()
        stockRepository.save(Stock(productId = PRODUCT_ID, quantity = INITIAL_QUANTITY))
    }

    // 실험 1
    @Test
    fun `락 없이 동시에 100건 재고 차감을 수행한다`() {
        // given
        val threadCount = 100
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        // when
        repeat(threadCount) {
            executor.submit {
                try {
                    val headers = HttpHeaders().apply {
                        contentType = MediaType.APPLICATION_JSON
                    }
                    val request = HttpEntity(StockRequest(productId = PRODUCT_ID, amount = 1), headers)
                    testRestTemplate.postForEntity("/stock-without-lock", request, Void::class.java)
                } catch (e: Exception) {
                    logger.error("Error during concurrent request", e)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then
        val stock = stockRepository.getStockByProductId(PRODUCT_ID)
        logger.info("Final quantity: ${stock.quantity}, Expected: 0")

        // 락이 없으므로 동시성 문제로 인해 0이 아닐 것으로 예상
        assertThat(stock.quantity).isNotEqualTo(0)
    }

    // 실험 2
    @Test
    fun `잘못된 트랜잭션과 Named Lock을 통해 동시에 100건 재고 차감을 수행한다`() {
        // given
        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        // when
        repeat(threadCount) {
            executor.submit {
                try {
                    val headers = HttpHeaders().apply {
                        contentType = MediaType.APPLICATION_JSON
                    }
                    val request = HttpEntity(StockRequest(productId = PRODUCT_ID, amount = 1), headers)
                    testRestTemplate.postForEntity("/stock-with-named-lock-wrong-transaction", request, Void::class.java)
                } catch (e: Exception) {
                    logger.error("Error during concurrent request", e)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then
        val stock = stockRepository.getStockByProductId(PRODUCT_ID)
        logger.info("Final quantity: ${stock.quantity}, Expected: 0")

        // Named Lock을 통해 락을 얻고 동시성 요청이 동기적으로 수행되므로 0이 됨
        assertThat(stock.quantity).isEqualTo(0)
    }


    // 실험 3
    @Test
    fun `트랜잭션 없이 Named Lock을 통해 동시에 100건 재고 차감을 수행한다`() {
        // given
        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        // when
        repeat(threadCount) {
            executor.submit {
                try {
                    val headers = HttpHeaders().apply {
                        contentType = MediaType.APPLICATION_JSON
                    }
                    val request = HttpEntity(StockRequest(productId = PRODUCT_ID, amount = 1), headers)
                    testRestTemplate.postForEntity("/stock-with-named-lock-without-transaction", request, Void::class.java)
                } catch (e: Exception) {
                    logger.error("Error during concurrent request", e)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then
        val stock = stockRepository.getStockByProductId(PRODUCT_ID)
        logger.info("Final quantity: ${stock.quantity}, Expected: 0")

        // Named Lock을 통해 락을 얻고 동시성 요청이 동기적으로 수행되므로 0이 됨
        assertThat(stock.quantity).isEqualTo(0)
    }



}