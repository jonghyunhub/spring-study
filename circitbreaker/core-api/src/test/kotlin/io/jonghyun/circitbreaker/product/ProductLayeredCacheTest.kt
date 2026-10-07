package io.jonghyun.circitbreaker.product

import com.github.benmanes.caffeine.cache.Caffeine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations

/**
 * L1(Caffeine) -> L2(Redis) layered cache 조회 로직 단위 테스트.
 * L2 조회(ProductRedisReader)는 mock 으로 대체하고, L1 hit / miss 에 따라 L2 호출이 어떻게 달라지는지 확인한다.
 */
class ProductLayeredCacheTest {

    private val productRedisReader = mockk<ProductRedisReader>()
    private val redisTemplate = mockk<RedisTemplate<String, Product>>()
    private val valueOperations = mockk<ValueOperations<String, Product>>()
    private lateinit var service: ProductCircuitBreakerService

    @BeforeEach
    fun setUp() {
        service = ProductCircuitBreakerService(
            redisTemplate = redisTemplate,
            productRepository = mockk(),
            productLocalCache = Caffeine.newBuilder().build(),
            productRedisReader = productRedisReader,
        )
    }

    @Test
    @DisplayName("L1에 적재된 상품은 L2(Redis)를 다시 조회하지 않는다")
    fun l1HitDoesNotCallL2() {
        every { productRedisReader.read(listOf(1L, 2L)) } returns mapOf(1L to product(1L), 2L to product(2L))

        service.findFromLayeredCache(listOf(1L, 2L))
        val result = service.findFromLayeredCache(listOf(1L, 2L))

        assertThat(result.map { it.status }).containsExactly("ON_SALE", "ON_SALE")
        verify(exactly = 1) { productRedisReader.read(any()) }
    }

    @Test
    @DisplayName("L1 miss 상품만 모아 L2를 조회한다")
    fun onlyL1MissIdsGoToL2() {
        every { productRedisReader.read(listOf(1L)) } returns mapOf(1L to product(1L))
        every { productRedisReader.read(listOf(2L, 3L)) } returns mapOf(2L to product(2L), 3L to product(3L))

        service.findFromLayeredCache(listOf(1L))
        val result = service.findFromLayeredCache(listOf(1L, 2L, 3L))

        assertThat(result.map { it.id }).containsExactly(1L, 2L, 3L)
        verify(exactly = 1) { productRedisReader.read(listOf(2L, 3L)) }
    }

    @Test
    @DisplayName("L2에도 없는 상품은 NONE으로 응답하고 L1에 적재하지 않는다")
    fun l2MissReturnsNoneAndIsNotCached() {
        every { productRedisReader.read(listOf(1L)) } returns emptyMap()

        val first = service.findFromLayeredCache(listOf(1L))
        service.findFromLayeredCache(listOf(1L))

        assertThat(first.single().status).isEqualTo(ProductCircuitBreakerService.STATUS_NONE)
        verify(exactly = 2) { productRedisReader.read(listOf(1L)) }
    }

    @Test
    @DisplayName("[비교군] findFromCache는 Redis에 없는 상품을 NONE으로 응답한다")
    fun findFromCacheReturnsNoneForMiss() {
        every { redisTemplate.opsForValue() } returns valueOperations
        every { valueOperations.multiGet(listOf("product:1", "product:2")) } returns listOf(product(1L), null)

        val result = service.findFromCache(listOf(1L, 2L))

        assertThat(result.map { it.status }).containsExactly("ON_SALE", ProductCircuitBreakerService.STATUS_NONE)
        assertThat(result.map { it.id }).containsExactly(1L, 2L)
    }

    private fun product(id: Long) = Product(id, "product-$id", "ON_SALE")
}
