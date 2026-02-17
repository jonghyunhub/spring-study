package io.jonghyun.MySQL.pessimisticlock

data class LockByIdRequest(
    val id: Long,
    val holdLockSeconds: Int = 0
)

data class LockByProductIdRequest(
    val productId: Long,
    val holdLockSeconds: Int = 0
)

data class LockByRangeRequest(
    val startId: Long,
    val endId: Long,
    val holdLockSeconds: Int = 0
)

data class LockByQuantityRequest(
    val quantity: Int,
    val holdLockSeconds: Int = 0
)

data class DecreaseStockRequest(
    val id: Long,
    val amount: Int
)
