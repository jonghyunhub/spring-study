package io.jonghyun.circitbreaker.config

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import io.jonghyun.circitbreaker.product.Product
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

/**
 * L1(Caffeine) 로컬 캐시 설정.
 * L1 -> L2(Redis) layered cache 를 도입했을 때 Lettuce 가 보내는 MGET 수가 얼마나 줄어드는지 실험하기 위한 캐시.
 */
@Configuration
class LocalCacheConfig {

    @Bean
    fun productLocalCache(
        meterRegistry: MeterRegistry,
        @Value("\${local-cache.product.maximum-size:100000}") maximumSize: Long,
        @Value("\${local-cache.product.ttl:30s}") ttl: Duration,
        @Value("\${local-cache.product.ttl-jitter:5s}") ttlJitter: Duration,
    ): Cache<Long, Product> {
        val cache = Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfter(JitterExpiry<Long, Product>(ttl, ttlJitter))
            .recordStats()
            .build<Long, Product>()
        // cache_gets_total{cache="product-local-cache", result="hit|miss"} 로 L1 hit 율 확인
        CaffeineCacheMetrics.monitor(meterRegistry, cache, PRODUCT_LOCAL_CACHE)
        return cache
    }

    /**
     * write 시점 기준 TTL + random jitter. 동시에 적재된 entry 들이 같은 시점에 만료되어 L2 로 몰리는 것을 방지한다.
     */
    class JitterExpiry<K : Any, V : Any>(ttl: Duration, jitter: Duration) : Expiry<K, V> {
        private val ttlNanos = ttl.toNanos()
        private val jitterNanos = jitter.toNanos()

        override fun expireAfterCreate(key: K, value: V, currentTime: Long): Long =
            if (jitterNanos > 0) ttlNanos + ThreadLocalRandom.current().nextLong(jitterNanos) else ttlNanos

        override fun expireAfterUpdate(key: K, value: V, currentTime: Long, currentDuration: Long): Long =
            expireAfterCreate(key, value, currentTime)

        override fun expireAfterRead(key: K, value: V, currentTime: Long, currentDuration: Long): Long =
            currentDuration
    }

    companion object {
        const val PRODUCT_LOCAL_CACHE = "product-local-cache"
    }
}
