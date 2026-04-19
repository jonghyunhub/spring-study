package io.jonghyun.circitbreaker.product

import io.jonghyun.circitbreaker.config.FeignConfig
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@FeignClient(
    name = "stub-service",
    url = "\${stub.service.url}",
    configuration = [FeignConfig::class],
)
interface StubServiceClient {

    @GetMapping("/external/products/{id}")
    fun getProduct(@PathVariable id: Long): ProductResponse
}
