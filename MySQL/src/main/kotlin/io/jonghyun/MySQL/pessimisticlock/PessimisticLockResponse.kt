package io.jonghyun.MySQL.pessimisticlock

data class LockResponse(
    val success: Boolean,
    val message: String,
    val stock: StockDto? = null,
    val stocks: List<StockDto>? = null
)

data class LockMonitoringResponse(
    val success: Boolean,
    val totalLocks: Int,
    val locks: List<LockInfo>
)
