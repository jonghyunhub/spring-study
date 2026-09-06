package io.jonghyun.Redis.cacheStampede

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/cacheable-sync")
class CacheableSyncController(
    private val cacheableSyncService: CacheableSyncService
) {
    @GetMapping("/no-sync")
    fun noSync(@RequestParam key: String) = cacheableSyncService.noSync(key)

    @GetMapping("/sync")
    fun sync(@RequestParam key: String) = cacheableSyncService.sync(key)

    @PostMapping("/reset")
    fun reset() = cacheableSyncService.resetStats()

    @GetMapping("/stats")
    fun stats() = cacheableSyncService.stats()
}