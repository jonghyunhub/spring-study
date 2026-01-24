package io.jonghyun.MySQL.namedlock

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class NamedLockStockController(
    val stackService: NamedLockStockService,
    val namedLockStockFacade: NamedLockStockFacade
) {

    @PostMapping("/stock-without-lock")
    fun decreaseStockWithOutLock(@RequestBody request: NamedLockStockRequest) {
        stackService.decreaseStockWithOutLock(productId = request.productId, amount = request.amount)
    }

    @PostMapping("/stock-with-named-lock-wrong-transaction")
    fun decreaseStockWithNamedLockWrongTransaction(@RequestBody request: NamedLockStockRequest) {
        stackService.decreaseStockWithNamedLockWrongTransaction(productId = request.productId, amount = request.amount)
    }

    @PostMapping("/stock-with-named-lock-without-transaction")
    fun decreaseStockWithNamedLockWithOutTransaction(@RequestBody request: NamedLockStockRequest) {
        stackService.decreaseStockWithNamedLockWithOutTransactional(productId = request.productId, amount = request.amount)
    }

    @PostMapping("/stock-with-named-lock")
    fun decreaseStockWithNamedLock(@RequestBody request: NamedLockStockRequest) {
        namedLockStockFacade.decreaseStock(productId = request.productId, amount = request.amount)
    }
}