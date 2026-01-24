package io.jonghyun.MySQL.optimisticlock

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OptimisticLockStockService(
    private val repository: OptimisticLockStockRepository
) {
    companion object {
        val logger = LoggerFactory.getLogger(OptimisticLockStockService::class.java)!!
    }

    /**
     * [락 없이 동작하는 차감 로직] => Lost Update 발생
     */
    @Transactional
    fun decreaseStockWithOutLock(productId: Long, amount: Int) {
        val stock = repository.getStockByProductId(productId)
        stock.decrease(amount)
        repository.save(stock)
    }




}