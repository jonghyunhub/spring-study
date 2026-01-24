package io.jonghyun.MySQL.namedlock

data class NamedLockStockRequest(
    val productId: Long,
    val amount: Int,
)
