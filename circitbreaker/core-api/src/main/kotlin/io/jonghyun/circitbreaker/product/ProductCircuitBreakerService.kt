package io.jonghyun.circitbreaker.product

import com.github.benmanes.caffeine.cache.Cache
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class ProductCircuitBreakerService(
    private val redisTemplate: RedisTemplate<String, Product>,
    private val productRepository: ProductRepository,
    private val productLocalCache: Cache<Long, Product>,
    private val productRedisReader: ProductRedisReader,
) {

    @CircuitBreaker(name = "redis-cache", fallbackMethod = "findFromDatabase")
    fun findFromCache(ids: List<Long>): List<ProductResponse> {
        val keys = ids.map { "product:$it" }

        // multiGet 은 keys 순서대로 값을 반환하고, Redis 에 없는 key 자리에는 null 이 들어있다.
        val values: List<Product?> = redisTemplate.opsForValue().multiGet(keys) ?: emptyList()

        val products = HashMap<Long, Product>()
        for (i in ids.indices) {
            val product = values.getOrNull(i) ?: continue
            products[ids[i]] = product
        }

        return ids.map { id ->
            products[id]?.let { ProductResponse(it.id, it.name, it.status) }
                ?: ProductResponse(id, "", STATUS_NONE)
        }
    }

    private fun findFromDatabase(ids: List<Long>, e: Exception): List<ProductResponse> {
        val products = productRepository.findAllById(ids)
        return products.map { ProductResponse(it.id, it.name, it.status) }
    }

    /**
     * L1(Caffeine) -> L2(Redis). 비교군은 findFromCache (L2 만 조회).
     * L1 miss id 만 모아 L2 를 일괄 조회하고(Cache Aside), 조회된 상품만 L1 에 적재한다. miss 는 NONE 으로 응답한다.
     */
    fun findFromLayeredCache(ids: List<Long>): List<ProductResponse> {
        // L1 조회
        val products = HashMap<Long, Product>()
        val l1Hits = productLocalCache.getAllPresent(ids)
        products.putAll(l1Hits)

        // L1 miss id 만 L2(Redis) read 후 L1 CacheAside
        val l1MissIds = ids.filterNot { products.containsKey(it) }.distinct()
        if (l1MissIds.isNotEmpty()) {
            val l2Products = productRedisReader.read(l1MissIds)
            productLocalCache.putAll(l2Products)
            products.putAll(l2Products)
        }

        return ids.map { id ->
            products[id]?.let { ProductResponse(it.id, it.name, it.status) }
                ?: ProductResponse(id, "", STATUS_NONE)
        }
    }

    companion object {
        const val STATUS_NONE = "NONE"
    }
}
