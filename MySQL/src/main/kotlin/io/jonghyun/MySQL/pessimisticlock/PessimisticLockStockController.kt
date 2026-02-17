package io.jonghyun.MySQL.pessimisticlock

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/pessimistic-lock")
class PessimisticLockStockController(
    private val stockService: PessimisticLockStockService,
    private val lockMonitoringService: LockMonitoringService
) {
    @PostMapping("/lock-by-primary-key")
    fun lockByPrimaryKey(@RequestBody request: LockByIdRequest): ResponseEntity<LockResponse> {
        val stock = stockService.lockByPrimaryKey(request.id, request.holdLockSeconds)
        return ResponseEntity.ok(
            LockResponse(
                success = true,
                message = "Lock acquired by primary key: id=${request.id}",
                stock = stock?.let { StockDto.from(it) }
            )
        )
    }

    @PostMapping("/lock-by-unique-index")
    fun lockByUniqueIndex(@RequestBody request: LockByProductIdRequest): ResponseEntity<LockResponse> {
        val stock = stockService.lockByUniqueIndex(request.productId, request.holdLockSeconds)
        return ResponseEntity.ok(
            LockResponse(
                success = true,
                message = "Lock acquired by unique index: productId=${request.productId}",
                stock = stock?.let { StockDto.from(it) }
            )
        )
    }


    @PostMapping("/lock-by-range")
    fun lockByRange(@RequestBody request: LockByRangeRequest): ResponseEntity<LockResponse> {
        val stocks = stockService.lockByRange(request.startId, request.endId, request.holdLockSeconds)
        return ResponseEntity.ok(
            LockResponse(
                success = true,
                message = "Range lock: ${request.startId}~${request.endId}, found ${stocks.size} records",
                stocks = StockDto.fromList(stocks)
            )
        )
    }

    @PostMapping("/lock-by-quantity")
    fun lockByQuantity(@RequestBody request: LockByQuantityRequest): ResponseEntity<LockResponse> {
        val stocks = stockService.lockByQuantity(request.quantity, request.holdLockSeconds)
        return ResponseEntity.ok(
            LockResponse(
                success = true,
                message = "Lock by quantity=${request.quantity}, found ${stocks.size} records",
                stocks = StockDto.fromList(stocks)
            )
        )
    }


    @PostMapping("/decrease-stock")
    fun decreaseStock(@RequestBody request: DecreaseStockRequest): ResponseEntity<LockResponse> {
        val stock = stockService.decreaseStock(request.id, request.amount)
        return ResponseEntity.ok(
            LockResponse(
                success = true,
                message = "Stock decreased: id=${request.id}, amount=${request.amount}",
                stock = StockDto.from(stock)
            )
        )
    }

    @PostMapping("/decrease-stock-without-lock")
    fun decreaseStockWithoutLock(@RequestBody request: DecreaseStockRequest): ResponseEntity<LockResponse> {
        val stock = stockService.decreaseStockWithoutLock(request.id, request.amount)
        return ResponseEntity.ok(
            LockResponse(
                success = true,
                message = "Stock decreased without lock: id=${request.id}, amount=${request.amount}",
                stock = StockDto.from(stock)
            )
        )
    }

    @GetMapping("/locks")
    fun getCurrentLocks(): ResponseEntity<LockMonitoringResponse> {
        val locks = lockMonitoringService.getCurrentLocks()
        return ResponseEntity.ok(
            LockMonitoringResponse(
                success = true,
                totalLocks = locks.size,
                locks = locks
            )
        )
    }
}
