package io.jonghyun.MySQL.lock

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource

@Service
class StockService(
    private val stockRepository: StockRepository,
) {

    companion object {
        val logger = LoggerFactory.getLogger(StockService::class.java)!!
    }

    /**
     * 락 없이 동작하는 차감 로직
     */
    @Transactional
    fun decreaseStockWithOutLock(productId: Long, amount: Int) {
        val stock = stockRepository.getStockByProductId(productId)
        stock.decrease(amount)
        stockRepository.save(stock)
    }
    

    @Transactional
    fun decreaseStockWithNamedLockWrongTransaction(productId: Long, amount: Int) {
        val lockKey = "stock:$productId"
        val lockTimeOutTime = 10
        try {
            val lockResult = stockRepository.getNamedLock(lockKey, lockTimeOutTime) // 락 획득 성공하면 1 리턴
            logger.info("GET_LOCK [$lockKey] result: $lockResult")

            // 락 획득 실패 시 예외 발생
            if (lockResult != 1) {
                throw IllegalStateException("Failed to acquire lock: $lockKey")
            }

            val stock = stockRepository.getStockByProductId(productId)
            stock.decrease(amount)
            stockRepository.save(stock)

        } finally {
            val releaseResult = stockRepository.releaseNamedLock(lockKey)
            logger.info("RELEASE_LOCK [$lockKey] result: $releaseResult") // 락이 제대로 해제 됐으면 1 리턴
        }
    }


    fun decreaseStockWithNamedLock(productId: Long, amount: Int) {
        val lockKey = "stock:$productId"
        val lockTimeOutTime = 10
        try {
            val lockResult = stockRepository.getNamedLock(lockKey, lockTimeOutTime) // 락 획득 성공하면 1 리턴
            logger.info("GET_LOCK [$lockKey] result: $lockResult")

            // 락 획득 실패 시 예외 발생
            if (lockResult != 1) {
                throw IllegalStateException("Failed to acquire lock: $lockKey")
            }

            val stock = stockRepository.getStockByProductId(productId)
            stock.decrease(amount)
            stockRepository.save(stock)
        } finally {
            val releaseResult = stockRepository.releaseNamedLock(lockKey)
            logger.info("RELEASE_LOCK [$lockKey] result: $releaseResult") // 락이 제대로 해제 됐으면 1 리턴
        }
    }

}