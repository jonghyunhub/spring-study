package io.jonghyun.circitbreaker.product

data class ProductResponse(
    val id: Long,
    val name: String,
    val status: String,
)
