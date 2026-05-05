package io.jonghyun.Redis.common

import io.jonghyun.Redis.product.Product
import io.jonghyun.Redis.product.ProductRepository
import io.jonghyun.Redis.caching.WriteBehindService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Write-Behind (Write-Back) 전략
 *
 * 읽기: Cache-Aside와 동일 (캐시 미스 시 DB 조회 후 캐시 저장)
 * 쓰기: 캐시만 즉시 갱신 + dirty-set 등록 → 스케줄러가 나중에 DB에 일괄 반영
 *
 * Write-Through와의 핵심 차이:
 *   Write-Through → 쓰기 시 DB + 캐시 동시 반영 (즉시 일관성)
 *   Write-Behind  → 쓰기 시 캐시만 반영, DB는 나중에 일괄 반영 (일시적 불일치 허용)
 */
class WriteBehindTest(
    private val writeBehindService: WriteBehindService,
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
        writeBehindService.evict(product.id)
        productRepository.deleteAll()
    }

    @Nested
    @DisplayName("쓰기 — flush 전 캐시·DB 불일치")
    inner class WriteBeforeFlush {

        @Test
        @DisplayName("flush 전: 캐시는 최신값, DB는 구 값 유지 (일시적 불일치)")
        fun cacheIsUpdatedButDbIsStale() {
            writeBehindService.updateProduct(product.id, "변경된 이름")

            val cached = writeBehindService.getProduct(product.id)
            assertThat(cached.name).isEqualTo("변경된 이름")

            val dbProduct = productRepository.findById(product.id).get()
            assertThat(dbProduct.name).isEqualTo("원래 이름")
        }

        @Test
        @DisplayName("flush 전: dirty-set에 id가 등록됨")
        fun idIsRegisteredInDirtySet() {
            writeBehindService.updateProduct(product.id, "변경된 이름")

            val dirtyMembers = redisTemplate.opsForSet().members(writeBehindService.dirtySetKey())
            assertThat(dirtyMembers).contains(product.id.toString())
        }
    }

    @Nested
    @DisplayName("flush — DB 동기화")
    inner class Flush {

        @Test
        @DisplayName("flush 후: DB에 최신값 반영, dirty-set 비워짐")
        fun dbSyncedAfterFlush() {
            writeBehindService.updateProduct(product.id, "변경된 이름")

            writeBehindService.flushPendingUpdates()

            val dbProduct = productRepository.findById(product.id).get()
            assertThat(dbProduct.name).isEqualTo("변경된 이름")

            val dirtyMembers = redisTemplate.opsForSet().members(writeBehindService.dirtySetKey())
            assertThat(dirtyMembers).isEmpty()
        }

        @Test
        @DisplayName("동일 id 여러 번 업데이트 → dirty-set에 1개만 유지, flush 시 최종값만 DB에 반영")
        fun deduplicatedAndFinalValueFlushed() {
            writeBehindService.updateProduct(product.id, "이름1")
            writeBehindService.updateProduct(product.id, "이름2")
            writeBehindService.updateProduct(product.id, "이름3")

            val dirtyMembers = redisTemplate.opsForSet().members(writeBehindService.dirtySetKey())
            assertThat(dirtyMembers).hasSize(1)

            writeBehindService.flushPendingUpdates()

            val dbProduct = productRepository.findById(product.id).get()
            assertThat(dbProduct.name).isEqualTo("이름3")
        }
    }
}
