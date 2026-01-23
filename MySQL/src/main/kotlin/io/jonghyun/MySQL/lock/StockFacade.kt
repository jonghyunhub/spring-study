package io.jonghyun.MySQL.lock

import org.springframework.stereotype.Service

@Service
class StockFacade(
    private val namedLockExecutor: NamedLockExecutor,
    private val stockService: StockService
) {
    fun decreaseStock(productId: Long, amount: Int) {
        val lockKey = "stock:$productId"
        
        namedLockExecutor.executeWithLock(lockKey) {
            // 이 블록은 별도 트랜잭션에서 실행됨
            stockService.decreaseStockWithOutLock(productId, amount)
        }
    }
}