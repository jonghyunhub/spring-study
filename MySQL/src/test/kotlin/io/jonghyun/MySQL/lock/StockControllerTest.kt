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
import javax.sql.DataSource

class StockControllerTest(
    private val dataSource: DataSource,
    private val testRestTemplate: TestRestTemplate,
    private val stockRepository: StockRepository
) : IntegrationTest() {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(StockControllerTest::class.java)
        const val PRODUCT_ID = 1L
        const val INITIAL_QUANTITY = 100
    }

    @BeforeEach
    fun setUp() {
        stockRepository.deleteAll()
        stockRepository.save(Stock(productId = PRODUCT_ID, quantity = INITIAL_QUANTITY))
    }

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

}