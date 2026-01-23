package io.jonghyun.MySQL.namedlock

data class StockRequest(
    val productId: Long,
    val amount: Int,
)
