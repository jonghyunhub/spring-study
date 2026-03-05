package io.jonghyun.Redis.common

import io.jonghyun.Redis.caching.Product
import io.jonghyun.Redis.caching.ProductCacheService
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
 * TTL 만료
 * 검증 목표: TTL 만료 전에는 캐시 히트, 만료 후에는 캐시 미스로 재적재.
 */
class ProductCacheTtlTest(
    private val productCacheService: ProductCacheService,
    private val productTemplateCacheService: ProductTemplateCacheService,
    private val productRepository: ProductRepository,
    private val cacheManager: CacheManager,
    private val redisTemplate: StringRedisTemplate,
) : IntegrationTest() {

    private lateinit var product: Product

    @BeforeEach
    fun setUp() {
        cacheManager.getCache("products-short-ttl")?.clear()
        product = productRepository.save(Product(name = "TTL 테스트 상품", price = 5000L))
    }

    @AfterEach
    fun tearDown() {
        cacheManager.getCache("products-short-ttl")?.clear()
        productTemplateCacheService.evict(product.id)
        productRepository.deleteAll()
    }

    @Test
    @DisplayName("@Cacheable - TTL 만료 전 캐시 존재, 만료 후 캐시 소멸 후 재적재")
    fun annotationTtlExpiry() {
        // when: 첫 번째 조회 → 캐시 적재 (TTL 2초)
        productCacheService.getProductWithShortTtl(product.id)

        // then: 캐시에 데이터 존재
        assertThat(cacheManager.getCache("products-short-ttl")?.get(product.id)).isNotNull()

        // when: TTL 만료 전 조회 → 캐시 히트
        productCacheService.getProductWithShortTtl(product.id)

        // then: 여전히 캐시 존재
        assertThat(cacheManager.getCache("products-short-ttl")?.get(product.id)).isNotNull()

        // when: TTL 만료 대기 (2초 TTL + 1초 여유)
        Thread.sleep(3000)

        // then: TTL 만료 → 캐시 소멸
        assertThat(cacheManager.getCache("products-short-ttl")?.get(product.id)).isNull()

        // when: 만료 후 재조회 → 캐시 재적재
        productCacheService.getProductWithShortTtl(product.id)
        assertThat(cacheManager.getCache("products-short-ttl")?.get(product.id)).isNotNull()
    }

    @Test
    @DisplayName("RedisTemplate - TTL 만료 전 Redis 키 존재, 만료 후 키 소멸 후 재적재")
    fun templateTtlExpiry() {
        val key = productTemplateCacheService.shortTtlCacheKey(product.id)

        // when: 첫 번째 조회 → Redis 적재 (TTL 2초)
        productTemplateCacheService.getProductWithShortTtl(product.id)

        // then: Redis에 키 존재
        assertThat(redisTemplate.hasKey(key)).isTrue()

        // when: TTL 만료 전 조회 → 캐시 히트
        productTemplateCacheService.getProductWithShortTtl(product.id)

        // then: 여전히 키 존재
        assertThat(redisTemplate.hasKey(key)).isTrue()

        // when: TTL 만료 대기
        Thread.sleep(3000)

        // then: TTL 만료 → Redis 키 소멸
        assertThat(redisTemplate.hasKey(key)).isFalse()

        // when: 만료 후 재조회 → Redis 재적재
        productTemplateCacheService.getProductWithShortTtl(product.id)
        assertThat(redisTemplate.hasKey(key)).isTrue()
    }
}