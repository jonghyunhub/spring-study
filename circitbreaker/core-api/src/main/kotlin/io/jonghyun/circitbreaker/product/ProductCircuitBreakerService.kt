package io.jonghyun.circitbreaker.product

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class ProductCircuitBreakerService(
    private val redisTemplate: RedisTemplate<String, Product>,
    private val productRepository: ProductRepository,
) {

    @CircuitBreaker(name = "redis-cache", fallbackMethod = "findFromDatabase")
    fun findFromCache(ids: List<Long>): List<ProductResponse> {
        val keys = ids.map { "product:$it" }

        val products = redisTemplate.opsForValue().multiGet(keys) ?: emptyList()

        return products.map { ProductResponse(it.id, it.name, it.status) }
    }

    private fun findFromDatabase(ids: List<Long>, e: Exception): List<ProductResponse> {
        val products = productRepository.findAllById(ids)
        return products.map { ProductResponse(it.id, it.name, it.status) }
    }
}
