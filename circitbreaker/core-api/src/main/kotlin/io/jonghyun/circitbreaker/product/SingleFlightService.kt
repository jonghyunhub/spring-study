package io.jonghyun.circitbreaker.product

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class SingleFlightService(
    private val redisTemplate: RedisTemplate<String, ProductDto>,
    private val productRepository: ProductRepository,
    private val singleFlight: SingleFlight<String, ProductDto>,
) {

    fun getProducts(id: Long): ProductDto {
        val key = "product:$id"

        redisTemplate.opsForValue().get(key)?.let { return it }

        return singleFlight.execute(key) {
            // double-check: 직전 leader가 이미 채웠을 수 있음
            val check = redisTemplate.opsForValue().get(key)
            if (check != null) return@execute check

            productRepository.findById(id)
                .orElseThrow { NoSuchElementException("상품이 존재하지 않습니다.") }.toDto()
                .also { redisTemplate.opsForValue().set(key, it, Duration.ofMinutes(10)) }
        }
    }

}