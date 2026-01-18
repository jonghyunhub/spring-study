package io.jonghyun.MySQL.lock

import org.springframework.data.jpa.repository.JpaRepository

interface StockRepository : JpaRepository<Stock, Long> {
    fun findByProductId(productId: Long): Stock?

    fun getStockByProductId(productId: Long): Stock =
        findByProductId(productId) ?: throw IllegalArgumentException("No stock found for productId=$productId")
}