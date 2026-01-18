package io.jonghyun.MySQL.lock

data class StockRequest(
    val productId: Long,
    val amount: Int,
)
