package io.jonghyun.MySQL.pessimisticlock

import io.jonghyun.MySQL.domain.Stock
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PessimisticLockStockService(
    private val stockRepository: PessimisticLockStockRepository
) {

    // pk - id 기반 조회
    @Transactional
    fun lockByPrimaryKey(id: Long, holdLockSeconds: Int = 0): Stock? {
        val stock = stockRepository.findByIdWithLock(id).orElse(null)
        if (holdLockSeconds > 0 && stock != null) {
            Thread.sleep(holdLockSeconds * 1000L)
        }
        return stock
    }

    // unique index - productId 기반 조회
    @Transactional
    fun lockByUniqueIndex(productId: Long, holdLockSeconds: Int = 0): Stock? {
        val stock = stockRepository.findByProductIdWithLock(productId).orElse(null)
        if (holdLockSeconds > 0 && stock != null) {
            Thread.sleep(holdLockSeconds * 1000L)
        }
        return stock
    }

    //  Gap Lock - 존재하지 않는 범위 조회
    @Transactional
    fun lockByRange(startId: Long, endId: Long, holdLockSeconds: Int = 0): List<Stock> {
        val stocks = stockRepository.findByIdBetweenWithLock(startId, endId)
        if (holdLockSeconds > 0) { // innoDB의 락은 트랜잭션이 커밋되면 자동으로 반납됨
            Thread.sleep(holdLockSeconds * 1000L) // 비즈니스 로직 수행으로 인한 커밋 지연
        }
        return stocks
    }

    // Next Key Lock - 범위 조회
    @Transactional
    fun lockByGreaterThanOrEqual(id: Long, holdLockSeconds: Int = 0): List<Stock> {
        val stocks = stockRepository.findByIdGreaterThanEqualWithLock(id)
        if (holdLockSeconds > 0) {
            Thread.sleep(holdLockSeconds * 1000L)
        }
        return stocks
    }

    // 인덱스 없는 컬럼 조회 (전체 테이블 락)
    @Transactional
    fun lockByQuantity(quantity: Int, holdLockSeconds: Int = 0): List<Stock> {
        val stocks = stockRepository.findByQuantityWithLock(quantity)
        if (holdLockSeconds > 0) {
            Thread.sleep(holdLockSeconds * 1000L)
        }
        return stocks
    }

    @Transactional
    fun decreaseStock(id: Long, amount: Int): Stock {
        val stock = stockRepository.findByIdWithLock(id).orElseThrow {
            IllegalArgumentException("Stock not found: id=$id")
        }
        stock.decrease(amount)
        return stockRepository.save(stock)
    }

    @Transactional
    fun create(productId: Long, quantity: Int): Stock {
        return stockRepository.save(Stock(productId = productId, quantity = quantity))
    }

    @Transactional
    fun decreaseStockWithoutLock(id: Long, amount: Int): Stock {
        val stock = stockRepository.findById(id).orElseThrow {
            IllegalArgumentException("Stock not found: id=$id")
        }
        stock.decrease(amount)
        return stockRepository.save(stock)
    }
}
