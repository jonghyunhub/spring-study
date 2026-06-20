package io.jonghyun.Redis.api

import io.jonghyun.Redis.caching.RedisTemplateService
import io.jonghyun.Redis.product.ProductDto
import io.jonghyun.Redis.product.RedisTemplateInfo
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class RedisTemplateController(
    private val redisTemplateService: RedisTemplateService
) {

    @GetMapping("redisTemplate/info")
    fun getInfo(): RedisTemplateInfo {
        return redisTemplateService.inspectRedisTemplate()
    }

    @PostMapping("redisTemplate/cache/products")
    fun writeProductInCache(@RequestBody request: CacheRequest) {
        if(request.ttlSecond != null) {
            return redisTemplateService.saveProductWithTtl(
                request.name,
                request.ttlSecond)
        }
        redisTemplateService.saveProductInCache(request.name)
    }

    @GetMapping("redisTemplate/cache/products/{name}")
    fun readProductInCache(@PathVariable name: String): ProductDto? {
        return redisTemplateService.getProductInCache(name)
    }

    @DeleteMapping("redisTemplate/cache/products/{name}")
    fun deleteProductInCache(@PathVariable name: String) {
        return redisTemplateService.evictProductInCache(name)
    }

    @PostMapping("redisTemplate/cache/products/list")
    fun writeProductsInCache(@RequestBody request: CacheRequest) {
        redisTemplateService.saveProductsInCache(request.name)
    }

    @GetMapping("redisTemplate/cache/products/list/{name}")
    fun readProductsInCache(@PathVariable name: String): List<ProductDto> {
        return redisTemplateService.getProductsInCache(name)
    }

    @PostMapping("redisTemplate/cache/products/hash")
    fun writeProductInCacheHash(@RequestBody request: CacheRequest) {
        redisTemplateService.saveProductHash(request.name)
    }

    @GetMapping("redisTemplate/cache/products/hash/{name}")
    fun readProductsInCacheHash(@PathVariable name: String): ProductDto {
        return redisTemplateService.findProductHash(name)
    }

    @PutMapping("redisTemplate/cache/products/hash")
    fun readProductsInCacheHash(@RequestBody request: ProductHashUpdateRequest): ProductDto {
        return redisTemplateService.updateProductPriceInHash(request.name, request.price)
    }

    @GetMapping("redisTemplate/cache/products/hash/{name}/price")
    fun readProductPriceInCacheHash(@PathVariable name: String): Int {
        return redisTemplateService.findProductPriceFromHash(name)
    }

    @PostMapping("redisTemplate/cache/products/byte")
    fun writeProductInCacheByByteArr(@RequestBody request: CacheRequest) {
        redisTemplateService.saveRawProduct(request.name)
    }

    @GetMapping("redisTemplate/cache/products/byte/{name}")
    fun readProductInCacheByByte(@PathVariable name: String) : ProductDto {
        return redisTemplateService.findRawProduct(name)
    }

    @GetMapping("redisTemplate/cache/products/byte/{name}/arr")
    fun readProductInCacheByByteArr(@PathVariable name: String) : ByteArray {
        val findRawBytes = redisTemplateService.findRawBytes(name)
        return findRawBytes
    }
}