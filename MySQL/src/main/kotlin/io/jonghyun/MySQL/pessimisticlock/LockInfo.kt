package io.jonghyun.MySQL.pessimisticlock

data class LockInfo(
    val engineLockId: String,
    val lockType: String,
    val lockMode: String,
    val lockStatus: String,
    val lockData: String?,
    val objectSchema: String,
    val objectName: String,
    val indexName: String?
)
