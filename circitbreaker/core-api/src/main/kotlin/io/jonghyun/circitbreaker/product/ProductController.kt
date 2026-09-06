package io.jonghyun.circitbreaker.product

import io.jonghyun.circitbreaker.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/products")
class ProductController(
    private val productService: ProductService,
    private val productCircuitBreakerService: ProductCircuitBreakerService
) {

    @GetMapping("/{id}")
    fun getProduct(@PathVariable id: Long): ApiResponse<ProductResponse> {
        return ApiResponse.success(productService.getProduct(id))
    }

    @PostMapping
    fun getProducts(@RequestBody request: ProductRequest): ApiResponse<List<ProductResponse>> {
        return ApiResponse.success(productCircuitBreakerService.findFromCache(request.ids))
    }
}
