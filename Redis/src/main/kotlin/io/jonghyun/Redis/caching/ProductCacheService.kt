package io.jonghyun.Redis.caching

import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * @Cacheable 방식
 */
@Service
class ProductCacheService(
    private val productRepository: ProductRepository
) {
    // 기본 TTL 60초 캐시
    @Cacheable(cacheNames = ["products"], key = "#id")
    fun getProduct(id: Long): ProductDto {
        val product = productRepository.findById(id)
            .orElseThrow { NoSuchElementException("Product not found: $id") }
        return product.toDto()
    }

    // 단기 TTL 2초 캐시 (TTL 테스트용)
    @Cacheable(cacheNames = ["products-short-ttl"], key = "#id")
    fun getProductWithShortTtl(id: Long): ProductDto {
        val product = productRepository.findById(id)
            .orElseThrow { NoSuchElementException("Product not found: $id") }
        return product.toDto()
    }

    // Cache-Aside 쓰기: 캐시 무효화 → 다음 읽기에서 DB 재조회
    @CacheEvict(cacheNames = ["products"], key = "#id")
    @Transactional
    fun updateProductName(id: Long, name: String): ProductDto {
        val product = productRepository.findById(id)
            .orElseThrow { NoSuchElementException("Product not found: $id") }
        product.name = name
        return productRepository.save(product).toDto()
    }
}