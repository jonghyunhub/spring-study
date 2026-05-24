package io.jonghyun.Redis.common

import io.jonghyun.Redis.caching.CacheAsideTemplateService
import io.jonghyun.Redis.caching.WriteThroughService
import io.jonghyun.Redis.product.Product
import io.jonghyun.Redis.product.ProductRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

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
            writeThroughService.preloadCache(product.id)
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

    @Nested
    @DisplayName("문제점 케이스1 : Cache Pollution")
    inner class CachePollution {
        @Test
        @DisplayName("[문제] 한 번도 읽히지 않는 데이터가 쓰기 후 캐시에 존재")
        fun writePollutesCaches() {
            val cacheKey = writeThroughService.cacheKey(product.id)
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse()

            writeThroughService.updateProduct(id = product.id, name = "변경된 이름")

            // 읽히지 않았지만 캐시에 존재 → 불필요한 메모리 점유
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue()
        }

        @Test
        @DisplayName("[해결] 기존 캐시 키 있을 때만 갱신 → 읽히지 않는 데이터는 캐시에 적재하지 않음")
        fun solutionUpdateOnlyIfCached() {
            val cacheKey = writeThroughService.cacheKey(product.id)
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse()

            // getProduct() 호출 없이 바로 업데이트
            writeThroughService.updateProductIfCached(product.id, "변경된 이름")

            // 캐시에 없던 데이터는 갱신하지 않음 → Cache Pollution 방지
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse()
        }

        @Test
        @DisplayName("[해결] 짧은 TTL을 통해 캐시 데이터 관리")
        fun solutionTTLCache() {
            val cacheKey = writeThroughService.cacheKey(product.id)
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse()

            val shortTtl = Duration.ofSeconds(2)
            writeThroughService.updateProductWithTtl(product.id, "변경된 이름", shortTtl)

            Thread.sleep(shortTtl.toMillis())

            // TTL 옵션을 통해 불필요한 캐시 데이터 자동 정리
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse()
        }
    }

    @Nested
    @DisplayName("문제점 케이스2 : Cold Start")
    inner class ColdStart {
        @Test
        @DisplayName("[문제] Cold Start — 쓰기 없이 읽기만 하면 캐시 미스")
        fun coldStartOnReadOnly() {
            val cacheKey = writeThroughService.cacheKey(product.id)
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse()

            writeThroughService.getProduct(product.id)

            // read 했으나 상품데이터 캐시에 적재하지 않음. CUD 연산이 없다면 계속 cache miss
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse()
        }

        @Test
        @DisplayName("[해결] Cache Warming — 사전 적재 후 첫 읽기에서 캐시 히트")
        fun solutionCacheWarming() {
            val cacheKey = writeThroughService.cacheKey(product.id)

            // 서버 기동 시 자주 읽히는 데이터 미리 적재
            writeThroughService.preloadCache(product.id)
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue()

            // 첫 읽기에서도 캐시 히트
            val result = writeThroughService.getProduct(product.id)
            assertThat(result.name).isEqualTo("원래 이름")
        }
    }
}
