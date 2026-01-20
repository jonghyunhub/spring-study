package io.jonghyun.MySQL.lock

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface StockRepository : JpaRepository<Stock, Long> {
    fun findByProductId(productId: Long): Stock?

    fun getStockByProductId(productId: Long): Stock =
        findByProductId(productId) ?: throw IllegalArgumentException("No stock found for productId=$productId")

    @Query(value = "select GET_LOCK(:key, :timeoutSeconds)", nativeQuery = true)
    fun getNamedLock(key: String, timeoutSeconds: Int): Int?


    @Query(value = "select RELEASE_LOCK(:key)", nativeQuery = true)
    fun releaseNamedLock(key: String): Int?
}