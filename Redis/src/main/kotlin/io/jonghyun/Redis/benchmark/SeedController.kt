package io.jonghyun.Redis.benchmark

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/benchmark")
class SeedController(
    private val seedService: SeedService,
) {
    @PostMapping("/seed")
    fun seed(@RequestParam count: Int): String {
        seedService.seed(count)
        return "seeded $count records"
    }
}
