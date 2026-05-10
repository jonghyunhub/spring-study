package io.jonghyun.Redis.caching

import io.jonghyun.Redis.product.ProductDto
import io.jonghyun.Redis.product.ProductRepository
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Cache-Aside 전략 — @Cacheable 선언적 방식
 *
 * 읽기: @Cacheable → 캐시 미스 시 DB 조회 후 자동 저장
 * 쓰기: @CacheEvict → DB 저장 후 캐시 무효화, 다음 읽기에서 재적재
 *
 * 장점: 선언적이고 간결, 스프링 캐시 추상화 활용
 * 단점: 쓰기 후 첫 읽기는 캐시 미스 (cold start), 내부 동작이 숨겨짐
 */
@Service
class CacheAsideService(
    private val productRepository: ProductRepository,
    private val cacheManager: CacheManager,
) {
    @Cacheable(cacheNames = ["products"], key = "#id")
    fun getProduct(id: Long): ProductDto =
        productRepository.findById(id)
            .orElseThrow { NoSuchElementException("Product not found: $id") }
            .toDto()

    @Cacheable(cacheNames = ["products-short-ttl"], key = "#id")
    fun getProductWithShortTtl(id: Long): ProductDto =
        productRepository.findById(id)
            .orElseThrow { NoSuchElementException("Product not found: $id") }
            .toDto()

    @CacheEvict(cacheNames = ["products"], key = "#id")
    @Transactional
    fun updateProduct(id: Long, name: String): ProductDto {
        val product = productRepository.findById(id)
            .orElseThrow { NoSuchElementException("Product not found: $id") }
        product.name = name
        return productRepository.save(product).toDto()
    }

    // [실험] @CacheEvict 없이 업데이트 → 캐시에 구 데이터 잔류 (stale cache)
    @Transactional
    fun updateProductWithoutEvict(id: Long, name: String): ProductDto {
        val product = productRepository.findById(id)
            .orElseThrow { NoSuchElementException("Product not found: $id") }
        product.name = name
        return productRepository.save(product).toDto()
    }


    // 트랜잭션, 캐시 사용시 주의점 -> 캐시 로직 { 트랜잭션 로직 { 비즈니스 로직 }} 형태로 적용해야함
    @Transactional
    @CacheEvict(cacheNames = ["products"], key = "#id")
    fun updateProductWrongCacheTransaction(id: Long, name: String): ProductDto {
        val product = productRepository.findById(id)
            .orElseThrow { NoSuchElementException("Product not found: $id") }
        product.name = name
        return productRepository.save(product).toDto()
    }

    // 혹은 아래와 같이 명시적으로 커밋 이후 캐시 적용하도록 설정
    @Transactional
    fun updateProductCacheWhenAfterCommit(id: Long, name: String): ProductDto {
        val product = productRepository.findById(id)
            .orElseThrow { NoSuchElementException("Product not found: $id") }
        product.name = name
        val result = productRepository.save(product).toDto()

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    cacheManager.getCache("products")?.evict(id)  // 커밋 이후 확실하게 삭제
                }
            }
        )
        return result
    }
}
