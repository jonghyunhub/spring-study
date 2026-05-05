package io.jonghyun.Redis.common

import io.jonghyun.Redis.caching.CacheAsideTemplateService
import io.jonghyun.Redis.product.Product
import io.jonghyun.Redis.product.ProductRepository
import io.jonghyun.Redis.caching.WriteThroughService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Write-Through 전략
 *
 * 읽기: Cache-Aside와 동일 (캐시 미스 시 DB 조회 후 캐시 저장)
 * 쓰기: DB 저장 + 즉시 캐시 갱신 (키 삭제 대신 값 업데이트)
 *
 * Cache-Aside 쓰기와의 핵심 차이:
 *   Cache-Aside → 쓰기 후 캐시 키 삭제 → 다음 읽기는 캐시 미스
 *   Write-Through → 쓰기 후 캐시 키 갱신 → 다음 읽기는 캐시 히트
 */
class WriteThroughTest(
    private val cacheAsideTemplateService: CacheAsideTemplateService,
    private val writeThroughService: WriteThroughService,
    private val productRepository: ProductRepository,
    private val redisTemplate: StringRedisTemplate,
) : IntegrationTest() {

    private lateinit var product: Product

    @BeforeEach
    fun setUp() {
        product = productRepository.save(Product(name = "원래 이름", price = 10000L))
    }

    @AfterEach
    fun tearDown() {
        cacheAsideTemplateService.evict(product.id)
        writeThroughService.evict(product.id)
        productRepository.deleteAll()
    }

    @Nested
    @DisplayName("쓰기 후 캐시 키 상태 비교")
    inner class WriteComparison {

        @Test
        @DisplayName("Cache-Aside - 쓰기 후 캐시 키 삭제 → 다음 읽기에서 캐시 미스 후 재적재")
        fun cacheAsideDeletesKeyAfterWrite() {
            val key = cacheAsideTemplateService.cacheKey(product.id)
            cacheAsideTemplateService.getProduct(product.id)
            assertThat(redisTemplate.hasKey(key)).isTrue()

            cacheAsideTemplateService.updateProduct(product.id, "변경된 이름")
            assertThat(redisTemplate.hasKey(key)).isFalse()

            val result = cacheAsideTemplateService.getProduct(product.id)
            assertThat(result.name).isEqualTo("변경된 이름")
            assertThat(redisTemplate.hasKey(key)).isTrue()
        }

        @Test
        @DisplayName("Write-Through - 쓰기 후 캐시 키 유지 (갱신) → 다음 읽기에서 캐시 히트")
        fun writeThroughUpdatesKeyAfterWrite() {
            val key = writeThroughService.cacheKey(product.id)
            writeThroughService.getProduct(product.id)
            assertThat(redisTemplate.hasKey(key)).isTrue()

            writeThroughService.updateProduct(product.id, "변경된 이름")
            assertThat(redisTemplate.hasKey(key)).isTrue()

            val result = writeThroughService.getProduct(product.id)
            assertThat(result.name).isEqualTo("변경된 이름")
        }
    }

    @Nested
    @DisplayName("읽기 — 캐시 히트/미스")
    inner class Read {

        @Test
        @DisplayName("Write-Through - 쓰기 직후 읽기에서 캐시 히트 (DB 조회 없음)")
        fun cacheHitAfterWrite() {
            writeThroughService.updateProduct(product.id, "변경된 이름")

            assertThat(redisTemplate.hasKey(writeThroughService.cacheKey(product.id))).isTrue()

            val result = writeThroughService.getProduct(product.id)
            assertThat(result.name).isEqualTo("변경된 이름")
        }
    }
}
