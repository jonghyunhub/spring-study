package io.jonghyun.Redis.common

import org.assertj.core.api.Assertions.assertThat
import io.jonghyun.Redis.caching.Product
import io.jonghyun.Redis.caching.ProductRepository
import io.jonghyun.Redis.caching.ProductTemplateCacheService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Cache-Aside vs Write-Through 비교
 * 검증 목표: 쓰기 직후 읽기 동작 차이. Cache-Aside → 캐시 키 삭제 후 재적재, Write-Through → 캐시 키 유지 및 즉시 갱신.
 */
class ProductWriteThroughTest(
    private val productTemplateCacheService: ProductTemplateCacheService,
    private val productRepository: ProductRepository,
    private val redisTemplate: StringRedisTemplate,
) : IntegrationTest() {

    private lateinit var product: Product

    @BeforeEach
    fun setUp() {
        product = productRepository.save(Product(name = "초기 이름", price = 10000L))
    }

    @AfterEach
    fun tearDown() {
        productTemplateCacheService.evict(product.id)
        productRepository.deleteAll()
    }

    @Test
    @DisplayName("Cache-Aside - 쓰기 후 캐시 삭제 → 다음 읽기에서 캐시 재적재")
    fun cacheAsideWriteThenRead() {
        val key = productTemplateCacheService.cacheKey(product.id)

        // given: 캐시에 데이터 저장
        productTemplateCacheService.getProduct(product.id)
        assertThat(redisTemplate.hasKey(key)).isTrue()

        // when: Cache-Aside 업데이트 → 캐시 삭제
        productTemplateCacheService.updateProductCacheAside(product.id, "변경된 이름")

        // then: 캐시 키 삭제됨
        assertThat(redisTemplate.hasKey(key)).isFalse()

        // when: 다음 읽기 → 캐시 미스 → DB 조회 후 캐시 재적재
        val result = productTemplateCacheService.getProduct(product.id)

        // then: 변경된 이름 반환, 캐시 재적재됨
        assertThat(result.name).isEqualTo("변경된 이름")
        assertThat(redisTemplate.hasKey(key)).isTrue()
    }

    @Test
    @DisplayName("Write-Through - 쓰기 후 캐시 즉시 갱신 → 다음 읽기에서 갱신된 값 반환")
    fun writeThroughThenRead() {
        val key = productTemplateCacheService.cacheKey(product.id)

        // given: 캐시에 데이터 저장
        productTemplateCacheService.getProduct(product.id)
        assertThat(redisTemplate.hasKey(key)).isTrue()

        // when: Write-Through 업데이트 → 캐시 즉시 갱신
        productTemplateCacheService.updateProductWriteThrough(product.id, "변경된 이름")

        // then: 캐시 키 유지됨 (삭제 없이 갱신)
        assertThat(redisTemplate.hasKey(key)).isTrue()

        // when: 다음 읽기 → 캐시 히트
        val result = productTemplateCacheService.getProduct(product.id)

        // then: 갱신된 이름 반환 (캐시에서)
        assertThat(result.name).isEqualTo("변경된 이름")
    }
}