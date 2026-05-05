package io.jonghyun.Redis.common

import io.jonghyun.Redis.caching.CacheAsideService
import io.jonghyun.Redis.caching.CacheAsideTemplateService
import io.jonghyun.Redis.product.Product
import io.jonghyun.Redis.product.ProductDto
import io.jonghyun.Redis.product.ProductRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Cache-Aside 전략 — @Cacheable vs RedisTemplate 비교
 *
 * 읽기: 캐시 미스 시 DB 조회 후 캐시 저장, 이후 캐시 히트
 * 쓰기: DB 저장 후 캐시 키 삭제 → 다음 읽기에서 재적재
 *
 * @Cacheable 방식: 어노테이션 선언만으로 동작, 내부 동작은 스프링이 처리
 * RedisTemplate 방식: 동일한 동작을 코드로 명시적으로 표현
 */
class CacheAsideTest(
    private val cacheAsideService: CacheAsideService,
    private val cacheAsideTemplateService: CacheAsideTemplateService,
    private val productRepository: ProductRepository,
    private val cacheManager: CacheManager,
    private val redisTemplate: StringRedisTemplate,
) : IntegrationTest() {

    private lateinit var product: Product

    @BeforeEach
    fun setUp() {
        cacheManager.getCache("products")?.clear()
        cacheManager.getCache("products-short-ttl")?.clear()
        product = productRepository.save(Product(name = "원래 이름", price = 10000L))
    }

    @AfterEach
    fun tearDown() {
        cacheManager.getCache("products")?.clear()
        cacheManager.getCache("products-short-ttl")?.clear()
        cacheAsideTemplateService.evict(product.id)
        productRepository.deleteAll()
    }

    @Nested
    @DisplayName("읽기 — 캐시 히트/미스")
    inner class Read {

        @Test
        @DisplayName("@Cacheable - 첫 조회 후 캐시 적재, 두 번째 조회는 캐시 히트")
        fun annotationCacheHitMiss() {
            val first = cacheAsideService.getProduct(product.id)
            assertThat(cacheManager.getCache("products")?.get(product.id, ProductDto::class.java)).isNotNull()

            val second = cacheAsideService.getProduct(product.id)
            assertThat(first).isEqualTo(second)
        }

        @Test
        @DisplayName("RedisTemplate - 첫 조회 후 Redis 키 적재, 두 번째 조회는 캐시 히트")
        fun templateCacheHitMiss() {
            val first = cacheAsideTemplateService.getProduct(product.id)
            assertThat(redisTemplate.hasKey(cacheAsideTemplateService.cacheKey(product.id))).isTrue()

            val second = cacheAsideTemplateService.getProduct(product.id)
            assertThat(first).isEqualTo(second)
        }
    }

    @Nested
    @DisplayName("쓰기 — 캐시 무효화 후 재적재")
    inner class Write {

        @Test
        @DisplayName("@Cacheable - 업데이트 후 @CacheEvict로 무효화, 다음 읽기에서 갱신된 데이터 반환")
        fun annotationCacheEvict() {
            cacheAsideService.getProduct(product.id)
            assertThat(cacheManager.getCache("products")?.get(product.id)).isNotNull()

            cacheAsideService.updateProduct(product.id, "변경된 이름")
            assertThat(cacheManager.getCache("products")?.get(product.id)).isNull()

            val result = cacheAsideService.getProduct(product.id)
            assertThat(result.name).isEqualTo("변경된 이름")
        }

        @Test
        @DisplayName("RedisTemplate - 업데이트 후 캐시 키 삭제, 다음 읽기에서 갱신된 데이터 반환")
        fun templateCacheAsideEvict() {
            cacheAsideTemplateService.getProduct(product.id)
            assertThat(redisTemplate.hasKey(cacheAsideTemplateService.cacheKey(product.id))).isTrue()

            cacheAsideTemplateService.updateProduct(product.id, "변경된 이름")
            assertThat(redisTemplate.hasKey(cacheAsideTemplateService.cacheKey(product.id))).isFalse()

            val result = cacheAsideTemplateService.getProduct(product.id)
            assertThat(result.name).isEqualTo("변경된 이름")
        }

        @Test
        @DisplayName("[실험] @CacheEvict 없이 업데이트 → 캐시에 구 데이터 잔류 (stale cache)")
        fun staleCacheWithoutEvict() {
            cacheAsideService.getProduct(product.id)

            cacheAsideService.updateProductWithoutEvict(product.id, "변경된 이름")

            val cached = cacheManager.getCache("products")?.get(product.id, ProductDto::class.java)
            assertThat(cached?.name).isEqualTo("원래 이름")

            assertThat(cacheAsideService.getProduct(product.id).name).isEqualTo("원래 이름")
        }
    }

    @Nested
    @DisplayName("TTL — 만료 전 히트, 만료 후 재적재")
    inner class Ttl {

        @Test
        @DisplayName("@Cacheable - TTL 만료 전 캐시 존재, 만료 후 소멸 및 재적재")
        fun annotationTtlExpiry() {
            cacheAsideService.getProductWithShortTtl(product.id)
            assertThat(cacheManager.getCache("products-short-ttl")?.get(product.id)).isNotNull()

            Thread.sleep(3000)
            assertThat(cacheManager.getCache("products-short-ttl")?.get(product.id)).isNull()

            cacheAsideService.getProductWithShortTtl(product.id)
            assertThat(cacheManager.getCache("products-short-ttl")?.get(product.id)).isNotNull()
        }

        @Test
        @DisplayName("RedisTemplate - TTL 만료 전 Redis 키 존재, 만료 후 소멸 및 재적재")
        fun templateTtlExpiry() {
            val key = cacheAsideTemplateService.shortTtlCacheKey(product.id)

            cacheAsideTemplateService.getProductWithShortTtl(product.id)
            assertThat(redisTemplate.hasKey(key)).isTrue()

            Thread.sleep(3000)
            assertThat(redisTemplate.hasKey(key)).isFalse()

            cacheAsideTemplateService.getProductWithShortTtl(product.id)
            assertThat(redisTemplate.hasKey(key)).isTrue()
        }
    }
}
