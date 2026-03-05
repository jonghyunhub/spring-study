package io.jonghyun.Redis.caching

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

/**
 * `@Cacheable`이 내부적으로 하는 일을 직접 코드로 드러낸다.
 */
@Service
class ProductTemplateCacheService(
    private val productRepository: ProductRepository,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    private val ttl = Duration.ofSeconds(60)
    private val shortTtl = Duration.ofSeconds(2)

    // Cache-Aside 읽기: 캐시 확인 → 미스 시 DB 조회 후 캐시 저장
    fun getProduct(id: Long): ProductDto {
        val key = cacheKey(id)
        val cached = redisTemplate.opsForValue().get(key)
        if (cached != null) {
            return objectMapper.readValue(cached)
        }
        val product = loadFromDb(id)
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(product.toDto()), ttl)
        return product.toDto()
    }

    fun getProductWithShortTtl(id: Long): ProductDto {
        val key = shortTtlCacheKey(id)
        val cached = redisTemplate.opsForValue().get(key)
        if (cached != null) {
            return objectMapper.readValue(cached)
        }
        val product = loadFromDb(id)
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(product.toDto()), shortTtl)
        return product.toDto()
    }

    // Cache-Aside 쓰기: DB 저장 → 캐시 삭제 (다음 읽기 시 DB 재조회)
    @Transactional
    fun updateProductCacheAside(id: Long, name: String): ProductDto {
        val product = loadFromDb(id)
        product.name = name
        val saved = productRepository.save(product)
        redisTemplate.delete(cacheKey(id))
        return saved.toDto()
    }

    // Write-Through 쓰기: DB 저장 → 즉시 캐시 갱신
    @Transactional
    fun updateProductWriteThrough(id: Long, name: String): ProductDto {
        val product = loadFromDb(id)
        product.name = name
        val saved = productRepository.save(product)
        redisTemplate.opsForValue().set(cacheKey(id), objectMapper.writeValueAsString(saved.toDto()), ttl)
        return saved.toDto()
    }

    fun evict(id: Long) {
        redisTemplate.delete(cacheKey(id))
        redisTemplate.delete(shortTtlCacheKey(id))
    }

    fun cacheKey(id: Long) = "template:products:$id"

    fun shortTtlCacheKey(id: Long) = "template:products-short-ttl:$id"

    private fun loadFromDb(id: Long): Product =
        productRepository.findById(id).orElseThrow { NoSuchElementException("Product not found: $id") }
}