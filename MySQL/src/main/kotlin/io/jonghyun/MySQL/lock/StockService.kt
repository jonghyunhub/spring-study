package io.jonghyun.MySQL.lock

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StockService(
    private val stockRepository: StockRepository,
) {
    @Transactional
    fun decreaseStockWithOutLock(productId: Long, amount: Int) {
        val stock = stockRepository.getStockByProductId(productId)
        stock.decrease(amount)
        stockRepository.save(stock)
    }

}