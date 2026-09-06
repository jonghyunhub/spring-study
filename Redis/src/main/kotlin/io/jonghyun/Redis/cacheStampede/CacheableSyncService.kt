package io.jonghyun.Redis.cacheStampede

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger

@Service
class CacheableSyncService {

    private val noSyncCount = AtomicInteger()
    private val syncCount = AtomicInteger()

    @Cacheable(cacheNames = ["cacheable-sync-no-sync"], key = "#key")
    fun noSync(key: String): String {
        val count = noSyncCount.incrementAndGet()
        Thread.sleep(3000)
        return "no-sync key=$key count=$count time=${System.currentTimeMillis()}"
    }

    @Cacheable(cacheNames = ["cacheable-sync-sync"], key = "#key", sync = true)
    fun sync(key: String): String {
        val count = syncCount.incrementAndGet()
        Thread.sleep(3000)
        return "sync key=$key count=$count time=${System.currentTimeMillis()}"
    }

    fun stats() = mapOf(
        "noSyncOriginCallCount" to noSyncCount.get(),
        "syncOriginCallCount" to syncCount.get(),
    )

    fun resetStats() {
        noSyncCount.set(0)
        syncCount.set(0)
    }
}