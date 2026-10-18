package io.jonghyun.circitbreaker.product

import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Component
class SingleFlight<K, V> {
    private val inFlight = ConcurrentHashMap<K, CompletableFuture<V>>()

    fun execute(key : K, loader: () -> V) : V {
        val newFuture = CompletableFuture<V>()
        // 이미 처리중인 메서드인지 확인 및 이미 있으면 결과 리턴 (follower 요청)
        val existing = inFlight.putIfAbsent(key, newFuture)
        if(existing != null) return existing.get(5, TimeUnit.SECONDS)

        try {
            // 앞선 요청이 없으면 db 호출후 결과 넣기 (leader 요청)
            return loader().also { newFuture.complete(it) }
        } catch (e: Throwable) {
            newFuture.completeExceptionally(e) // follower 들에게 실패 알림
            throw e
        } finally {
            inFlight.remove(key)
        }
    }
}