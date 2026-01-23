package io.jonghyun.MySQL.lock

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class StockController(
    val stackService: StockService
) {

    @PostMapping("/stock-without-lock")
    fun decreaseStockWithOutLock(@RequestBody request: StockRequest) {
        stackService.decreaseStockWithOutLock(productId = request.productId, amount = request.amount)
    }

    @PostMapping("/stock-with-named-lock-wrong-transaction")
    fun decreaseStockWithNamedLockWrongTransaction(@RequestBody request: StockRequest) {
        stackService.decreaseStockWithNamedLockWithOutTransactional(productId = request.productId, amount = request.amount)
    }

    @PostMapping("/stock-with-named-lock-without-transaction")
    fun decreaseStockWithNamedLockWithOutTransaction(@RequestBody request: StockRequest) {
        stackService.decreaseStockWithNamedLockWithOutTransactional(productId = request.productId, amount = request.amount)
    }
}