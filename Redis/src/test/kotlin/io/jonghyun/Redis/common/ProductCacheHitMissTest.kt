package io.jonghyun.Redis.common

import io.jonghyun.Redis.caching.Product
import io.jonghyun.Redis.caching.ProductCacheService
import io.jonghyun.Redis.caching.ProductDto
import io.jonghyun.Redis.caching.ProductRepository
import io.jonghyun.Redis.caching.ProductTemplateCacheService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * 캐시 히트/미스
 * 검증 목표: 첫 번째 조회 후 캐시 적재, 두 번째 조회에서 캐시 히트(동일 결과 반환).
 */
class ProductCacheHitMissTest(
    private val productCacheService: ProductCacheService,
    private val productTemplateCacheService: ProductTemplateCacheService,
    private val productRepository: ProductRepository,
    private val cacheManager: CacheManager,
    private val redisTemplate: StringRedisTemplate,
) : IntegrationTest() {

    private lateinit var product: Product

    @BeforeEach
    fun setUp() {
        cacheManager.getCache("products")?.clear()
        product = productRepository.save(Product(name = "테스트 상품", price = 10000L))
    }

    @AfterEach
    fun tearDown() {
        cacheManager.getCache("products")?.clear()
        productTemplateCacheService.evict(product.id)
        productRepository.deleteAll()
    }

    @Test
    @DisplayName("@Cacheable - 첫 번째 조회 후 캐시 적재, 두 번째 조회는 캐시 히트")
    fun annotationCacheHitMiss() {
        // when: 첫 번째 조회 → 캐시 미스 → DB 조회 후 캐시 적재
        val firstResult = productCacheService.getProduct(product.id)

        // then: 캐시에 데이터 적재됨
        val cachedValue = cacheManager.getCache("products")?.get(product.id, ProductDto::class.java)
        assertThat(cachedValue).isNotNull()
        assertThat(cachedValue!!.id).isEqualTo(product.id)

        // when: 두 번째 조회 → 캐시 히트
        val secondResult = productCacheService.getProduct(product.id)

        // then: 캐시에서 동일한 결과 반환
        assertThat(firstResult).isEqualTo(secondResult)
    }

    @Test
    @DisplayName("RedisTemplate - 첫 번째 조회 후 Redis 키 적재, 두 번째 조회는 캐시 히트")
    fun templateCacheHitMiss() {
        productTemplateCacheService.evict(product.id)

        // when: 첫 번째 조회 → 캐시 미스 → DB 조회 후 Redis 적재
        val firstResult = productTemplateCacheService.getProduct(product.id)

        // then: Redis에 키 존재
        assertThat(redisTemplate.hasKey(productTemplateCacheService.cacheKey(product.id))).isTrue()

        // when: 두 번째 조회 → 캐시 히트
        val secondResult = productTemplateCacheService.getProduct(product.id)

        // then: 동일한 결과 반환
        assertThat(firstResult).isEqualTo(secondResult)
    }
}