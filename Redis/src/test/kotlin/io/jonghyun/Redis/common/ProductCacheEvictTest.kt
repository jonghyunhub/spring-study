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

/**
 * 캐시 무효화 후 갱신
 * 검증 목표: `@CacheEvict` 후 다음 읽기에서 변경된 데이터를 가져온다.
 */
class ProductCacheEvictTest(
    private val productCacheService: ProductCacheService,
    private val productTemplateCacheService: ProductTemplateCacheService,
    private val cacheManager: CacheManager,
    private val productRepository: ProductRepository,
) : IntegrationTest() {

    private lateinit var product: Product

    @BeforeEach
    fun setUp() {
        cacheManager.getCache("products")?.clear()
        product = productRepository.save(Product(name = "원래 이름", price = 10000L))
    }

    @AfterEach
    fun tearDown() {
        cacheManager.getCache("products")?.clear()
        productTemplateCacheService.evict(product.id)
        productRepository.deleteAll()
    }

    @Test
    @DisplayName("@CacheEvict - 업데이트 후 캐시 무효화, 다음 읽기에서 갱신된 데이터 반환")
    fun annotationCacheEvict() {
        // given: 캐시 저장
        val before = productCacheService.getProduct(product.id)
        assertThat(before.name).isEqualTo("원래 이름")
        assertThat(cacheManager.getCache("products")?.get(product.id)).isNotNull()

        // when: 이름 변경 + @CacheEvict
        productCacheService.updateProductName(product.id, "변경된 이름")

        // then: 캐시가 무효화됨
        assertThat(cacheManager.getCache("products")?.get(product.id)).isNull()

        // when: 다음 읽기 → 캐시 미스 → DB에서 변경된 데이터 반환
        val after = productCacheService.getProduct(product.id)
        assertThat(after.name).isEqualTo("변경된 이름")
    }

    @Test
    @DisplayName("RedisTemplate Cache-Aside - 업데이트 후 캐시 삭제, 다음 읽기에서 갱신된 데이터 반환")
    fun templateCacheAsideEvict() {
        // given: 캐시 저장
        val before = productTemplateCacheService.getProduct(product.id)
        assertThat(before.name).isEqualTo("원래 이름")

        // when: Cache-Aside 업데이트 (DB 저장 → 캐시 삭제)
        productTemplateCacheService.updateProductCacheAside(product.id, "변경된 이름")

        // then: 캐시 키 삭제됨 (다음 읽기에서 DB 재조회)
        val after = productTemplateCacheService.getProduct(product.id)
        assertThat(after.name).isEqualTo("변경된 이름")
    }
}