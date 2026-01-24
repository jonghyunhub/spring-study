package io.jonghyun.MySQL.namedlock

import org.springframework.stereotype.Service

@Service
class NamedLockStockFacade(
    private val namedLockExecutor: NamedLockExecutor,
    private val namedLockStockService: NamedLockStockService
) {
    fun decreaseStock(productId: Long, amount: Int) {
        val lockKey = "stock:$productId"
        
        namedLockExecutor.executeWithLock(lockKey) {
            // 이 블록은 별도 트랜잭션에서 실행됨
            namedLockStockService.decreaseStockWithOutLock(productId, amount)
        }
    }
}