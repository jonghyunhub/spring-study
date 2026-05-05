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
 * Write-Through 전략 — RedisTemplate 명령형 방식
 *
 * 읽기: 캐시 확인 → 미스 시 DB 조회 후 캐시 저장 (Cache-Aside 읽기와 동일)
 * 쓰기: DB 저장 + 즉시 캐시 갱신 (키 삭제가 아닌 갱신)
 *
 * Cache-Aside 쓰기와의 차이:
 *   Cache-Aside → 캐시 키 삭제 → 다음 읽기에서 캐시 미스 후 재적재
 *   Write-Through → 캐시 키 갱신 → 다음 읽기에서 바로 캐시 히트
 *
 * 장점: 쓰기 후 즉시 캐시 히트 가능, 읽기 일관성 높음
 * 단점: 읽히지 않는 데이터도 캐시에 저장되어 자원 낭비 가능
 */
@Service
class WriteThroughService(
    private val productRepository: ProductRepository,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    private val ttl = Duration.ofSeconds(60)

    fun getProduct(id: Long): ProductDto {
        val cached = redisTemplate.opsForValue().get(cacheKey(id))
        if (cached != null) return objectMapper.readValue(cached)

        val product = loadFromDb(id)
        redisTemplate.opsForValue().set(cacheKey(id), objectMapper.writeValueAsString(product.toDto()), ttl)
        return product.toDto()
    }

    @Transactional
    fun updateProduct(id: Long, name: String): ProductDto {
        val product = loadFromDb(id)
        product.name = name
        val saved = productRepository.save(product)
        redisTemplate.opsForValue().set(cacheKey(id), objectMapper.writeValueAsString(saved.toDto()), ttl)
        return saved.toDto()
    }

    fun evict(id: Long) = redisTemplate.delete(cacheKey(id))

    fun cacheKey(id: Long) = "write-through:products:$id"

    private fun loadFromDb(id: Long): Product =
        productRepository.findById(id).orElseThrow { NoSuchElementException("Product not found: $id") }
}
