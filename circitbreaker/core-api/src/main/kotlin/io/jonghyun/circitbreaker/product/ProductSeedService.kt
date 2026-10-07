package io.jonghyun.circitbreaker.product

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

/**
 * layered cache 실험용 데이터 적재.
 * - DB: id 1..count 전체 상품
 * - Redis: id % 10 < ratio 인 상품만 적재 (ratio=10 → 100%, ratio=3 → 30%)
 *   인기 순위(id 가 작을수록 인기, k6 zipf)와 무관하게 고르게 분포시킨다.
 * Redis 값은 GenericJackson2JsonRedisSerializer 의 @class 타입 정보가 필요하므로 RedisTemplate 으로 적재한다.
 */
@Service
class ProductSeedService(
    private val jdbcTemplate: JdbcTemplate,
    private val redisTemplate: RedisTemplate<String, Product>,
) {

    fun seed(count: Int, ratio: Int): String {
        val products = (1L..count).map { Product(it, "product-$it", "ON_SALE") }

        jdbcTemplate.update("DELETE FROM product")
        jdbcTemplate.batchUpdate(
            "INSERT INTO product (id, name, status) VALUES (?, ?, ?)",
            products,
            BATCH_SIZE,
        ) { ps, product ->
            ps.setLong(1, product.id)
            ps.setString(2, product.name)
            ps.setString(3, product.status)
        }

        val (cached, notCached) = products.partition { it.id % 10 < ratio }
        cached.chunked(BATCH_SIZE).forEach { chunk ->
            redisTemplate.opsForValue().multiSet(chunk.associateBy { "product:${it.id}" })
        }
        notCached.chunked(BATCH_SIZE).forEach { chunk ->
            redisTemplate.delete(chunk.map { "product:${it.id}" })
        }

        return "seeded db=$count redis=${cached.size} (ratio=$ratio/10)"
    }

    companion object {
        private const val BATCH_SIZE = 1000
    }
}
