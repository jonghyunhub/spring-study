package io.jonghyun.Redis.benchmark

data class GoodsDto(
    val id: Long,
    val name: String,
    val price: Long,
    val stock: Int,
    val category: String,
)
