package io.jonghyun.MySQL.pessimisticlock

import io.jonghyun.MySQL.domain.Stock
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface PessimisticLockStockRepository : JpaRepository<Stock, Long> {

    @Query("SELECT s FROM Stock s WHERE s.productId = :productId")
    fun getStockByProductId(@Param("productId") productId: Long): Stock

    // === Record Lock 실험용 메서드 ===

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.id = :id")
    fun findByIdWithLock(@Param("id") id: Long): Optional<Stock>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.productId = :productId")
    fun findByProductIdWithLock(@Param("productId") productId: Long): Optional<Stock>

    // === Gap Lock / Next Key Lock 실험용 메서드 ===

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.id BETWEEN :startId AND :endId")
    fun findByIdBetweenWithLock(
        @Param("startId") startId: Long,
        @Param("endId") endId: Long
    ): List<Stock>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.id >= :id")
    fun findByIdGreaterThanEqualWithLock(@Param("id") id: Long): List<Stock>

    // === 인덱스 없는 컬럼 조회 (전체 테이블 락) ===

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.quantity = :quantity")
    fun findByQuantityWithLock(@Param("quantity") quantity: Int): List<Stock>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.quantity BETWEEN :minQuantity AND :maxQuantity")
    fun findByQuantityBetweenWithLock(
        @Param("minQuantity") minQuantity: Int,
        @Param("maxQuantity") maxQuantity: Int
    ): List<Stock>
}