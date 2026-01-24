package io.jonghyun.MySQL.optimisticlock

import io.jonghyun.MySQL.domain.Stock
import org.springframework.data.jpa.repository.JpaRepository

interface OptimisticLockStockRepository : JpaRepository<Stock, Long> {
    fun findByProductId(productId: Long): Stock?

    fun getStockByProductId(productId: Long): Stock =
        findByProductId(productId) ?: throw IllegalArgumentException("No stock found for productId=$productId")



}