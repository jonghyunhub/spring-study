package io.jonghyun.Redis.caching

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.jonghyun.Redis.product.Product
import io.jonghyun.Redis.product.ProductDto
import io.jonghyun.Redis.product.ProductRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

/**
 * Cache-Aside 전략 — RedisTemplate 명령형 방식
 *
 * 읽기: 캐시 확인 → 미스 시 DB 조회 후 직접 저장 (@Cacheable이 내부적으로 하는 일을 코드로 드러냄)
 * 쓰기: DB 저장 → 캐시 키 삭제 (다음 읽기 시 DB 재조회)
 *
 * 장점: 내부 동작 명시적, TTL·직렬화 직접 제어 가능
 * 단점: 보일러플레이트 증가, 쓰기 후 첫 읽기는 캐시 미스
 */
@Service
class CacheAsideTemplateService(
    private val productRepository: ProductRepository,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    private val ttl = Duration.ofSeconds(60)
    private val shortTtl = Duration.ofSeconds(2)

    fun getProduct(id: Long): ProductDto {
        val cached = redisTemplate.opsForValue().get(cacheKey(id))
        if (cached != null) return objectMapper.readValue(cached)

        val product = loadFromDb(id)
        redisTemplate.opsForValue().set(cacheKey(id), objectMapper.writeValueAsString(product.toDto()), ttl)
        return product.toDto()
    }

    fun getProductWithShortTtl(id: Long): ProductDto {
        val cached = redisTemplate.opsForValue().get(shortTtlCacheKey(id))
        if (cached != null) return objectMapper.readValue(cached)

        val product = loadFromDb(id)
        redisTemplate.opsForValue().set(shortTtlCacheKey(id), objectMapper.writeValueAsString(product.toDto()), shortTtl)
        return product.toDto()
    }

    @Transactional
    fun updateProduct(id: Long, name: String): ProductDto {
        val product = loadFromDb(id)
        product.name = name
        val saved = productRepository.save(product)
        redisTemplate.delete(cacheKey(id))
        return saved.toDto()
    }

    fun evict(id: Long) {
        redisTemplate.delete(cacheKey(id))
        redisTemplate.delete(shortTtlCacheKey(id))
    }

    fun cacheKey(id: Long) = "cache-aside:products:$id"
    fun shortTtlCacheKey(id: Long) = "cache-aside:products-short-ttl:$id"

    private fun loadFromDb(id: Long): Product =
        productRepository.findById(id).orElseThrow { NoSuchElementException("Product not found: $id") }
}
