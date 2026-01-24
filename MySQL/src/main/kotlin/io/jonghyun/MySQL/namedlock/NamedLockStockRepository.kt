package io.jonghyun.MySQL.namedlock

import io.jonghyun.MySQL.domain.Stock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NamedLockStockRepository : JpaRepository<Stock, Long> {
    fun findByProductId(productId: Long): Stock?

    fun getStockByProductId(productId: Long): Stock =
        findByProductId(productId) ?: throw IllegalArgumentException("No stock found for productId=$productId")

    @Query(value = "select GET_LOCK(:key, :timeoutSeconds)", nativeQuery = true)
    fun getNamedLock(key: String, timeoutSeconds: Int): Int?


    @Query(value = "select RELEASE_LOCK(:key)", nativeQuery = true)
    fun releaseNamedLock(key: String): Int?

    @Query(
        value = "SELECT GET_LOCK(:key, :timeout) as lock_result, CONNECTION_ID() as conn_id",
        nativeQuery = true
    )
    fun getNamedLockWithConnectionId(
        @Param("key") key: String,
        @Param("timeout") timeout: Int
    ): NamedLockResult

    @Query(
        value = "SELECT RELEASE_LOCK(:key) as release_result, CONNECTION_ID() as conn_id",
        nativeQuery = true
    )
    fun releaseNamedLockWithConnectionId(@Param("key") key: String): NamedLockResult
}