package io.jonghyun.circitbreaker.product

import io.jonghyun.circitbreaker.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/products")
class ProductController(
    private val productService: ProductService,
    private val productCircuitBreakerService: ProductCircuitBreakerService,
    private val productSeedService: ProductSeedService,
) {

    @GetMapping("/{id}")
    fun getProduct(@PathVariable id: Long): ApiResponse<ProductResponse> {
        return ApiResponse.success(productService.getProduct(id))
    }

    @PostMapping
    fun getProducts(@RequestBody request: ProductRequest): ApiResponse<List<ProductResponse>> {
        return ApiResponse.success(productCircuitBreakerService.findFromCache(request.ids))
    }

    /** L1(Caffeine) -> L2(Redis) */
    @PostMapping("/layered-cache")
    fun getProductsFromLayeredCache(@RequestBody request: ProductRequest): ApiResponse<List<ProductResponse>> {
        return ApiResponse.success(productCircuitBreakerService.findFromLayeredCache(request.ids))
    }

    /** layered cache 실험 데이터 적재 (ratio: Redis 적재 비율, 10=100%, 3=30%) */
    @PostMapping("/seed")
    fun seed(@RequestParam count: Int, @RequestParam(defaultValue = "10") ratio: Int): ApiResponse<String> {
        return ApiResponse.success(productSeedService.seed(count, ratio))
    }
}
