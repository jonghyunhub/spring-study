package io.jonghyun.circitbreaker.product

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

/**
 * L2(Redis) 상품 조회.
 * Redis 에는 일부 상품만 적재되어 있으므로 결과에 없는 id 는 miss 로 본다 (DB 를 조회하지 않음).
 * Redis 장애(failed) 또는 CB OPEN(not_permitted) 시에만 DB 로 fallback 한다.
 *
 * ProductCircuitBreakerService 에서 분리한 이유:
 * 같은 클래스 안에서 @CircuitBreaker 메서드를 호출하면 AOP 프록시를 거치지 않아 CB 가 동작하지 않는다(self-invocation).
 */
@Component
class ProductRedisReader(
    private val redisTemplate: RedisTemplate<String, Product>,
    private val productRepository: ProductRepository,
) {

    @CircuitBreaker(name = "redis-cache", fallbackMethod = "readFromDatabase")
    fun read(ids: List<Long>): Map<Long, Product> {
        val keys = ids.map { "product:$it" }

        // Redis Cluster 에서는 Lettuce 가 key 를 slot 별로 나눠 slot 마다 MGET 을 1개씩 보낸다.
        val products = redisTemplate.opsForValue().multiGet(keys) ?: return emptyMap()

        return ids.zip(products)
            .mapNotNull { (id, product) -> product?.let { id to it } }
            .toMap()
    }

    private fun readFromDatabase(ids: List<Long>, e: Exception): Map<Long, Product> =
        productRepository.findAllById(ids).associateBy { it.id }
}
