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
 * Write-Behind (Write-Back) 전략 — RedisTemplate 명령형 방식
 *
 * 읽기: 캐시 확인 → 미스 시 DB 조회 후 캐시 저장
 * 쓰기: 캐시만 즉시 갱신 + dirty-set 등록 → 스케줄러가 나중에 DB에 일괄 반영
 *
 * 장점: 쓰기 응답이 빠름 (DB I/O 없음), 여러 쓰기를 배치로 처리 가능
 * 단점: flush 전까지 캐시·DB 일시적 불일치, Redis 장애 시 미반영 데이터 유실 위험
 *
 * 주의: 캐시 TTL은 flush 주기(5초)보다 충분히 길어야 flush 전 캐시 만료로 인한 데이터 유실 방지
 */
@Service
class WriteBehindService(
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

    fun updateProduct(id: Long, name: String): ProductDto {
        val product = loadFromDb(id)
        val dto = ProductDto(id = id, name = name, price = product.price)
        redisTemplate.opsForValue().set(cacheKey(id), objectMapper.writeValueAsString(dto), ttl)
        redisTemplate.opsForSet().add(dirtySetKey(), id.toString())
        return dto
    }

    // dirty-set에 등록된 항목을 DB에 일괄 반영 (WriteBehindFlushScheduler에서 호출, 테스트에서 직접 호출 가능)
    @Transactional
    fun flushPendingUpdates() {
        val dirtyIds = redisTemplate.opsForSet().members(dirtySetKey()) ?: return
        if (dirtyIds.isEmpty()) return

        for (rawId in dirtyIds) {
            val id = rawId.toLong()
            val cached = redisTemplate.opsForValue().get(cacheKey(id)) ?: continue
            val dto = objectMapper.readValue<ProductDto>(cached)
            val product = productRepository.findById(id).orElse(null) ?: continue
            product.name = dto.name
            productRepository.save(product)
            redisTemplate.opsForSet().remove(dirtySetKey(), rawId)
        }
    }

    fun evict(id: Long) {
        redisTemplate.delete(cacheKey(id))
        redisTemplate.opsForSet().remove(dirtySetKey(), id.toString())
    }

    fun cacheKey(id: Long) = "write-behind:products:$id"
    fun dirtySetKey() = "write-behind:products:dirty-set"

    private fun loadFromDb(id: Long): Product =
        productRepository.findById(id).orElseThrow { NoSuchElementException("Product not found: $id") }
}
