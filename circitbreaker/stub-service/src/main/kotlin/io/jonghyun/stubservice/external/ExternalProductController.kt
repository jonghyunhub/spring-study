package io.jonghyun.stubservice.external

import io.jonghyun.stubservice.control.StubMode
import io.jonghyun.stubservice.control.StubStateHolder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/external")
class ExternalProductController {

    @GetMapping("/products/{id}")
    fun getProduct(@PathVariable id: Long): ResponseEntity<Any> {
        return when (StubStateHolder.mode) {
            StubMode.FAIL -> ResponseEntity
                .internalServerError()
                .body(mapOf("error" to "Service unavailable"))

            StubMode.SLOW -> {
                Thread.sleep(StubStateHolder.slowDelayMs)
                ResponseEntity.ok(ProductResponse(id = id, name = "Product $id", status = "SLOW"))
            }

            StubMode.NORMAL -> ResponseEntity.ok(ProductResponse(id = id, name = "Product $id", status = "OK"))
        }
    }
}

data class ProductResponse(val id: Long, val name: String, val status: String)
