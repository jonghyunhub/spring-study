package io.jonghyun.Redis.benchmark

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/benchmark")
class BenchmarkController(
    private val benchmarkService: BenchmarkService,
) {
    @PostMapping("/seed")
    fun seed(@RequestParam count: Int): String {
        benchmarkService.seed(count)
        return "seeded $count records"
    }

    @GetMapping("/mysql/{id}")
    fun findByMysql(@PathVariable id: Long): GoodsDto =
        benchmarkService.findByMysql(id)

    @GetMapping("/redis-string/{id}")
    fun findByRedisString(@PathVariable id: Long): GoodsDto =
        benchmarkService.findByRedisString(id)

    @GetMapping("/redis-hash/{id}")
    fun findByRedisHash(@PathVariable id: Long): GoodsDto =
        benchmarkService.findByRedisHash(id)
}
