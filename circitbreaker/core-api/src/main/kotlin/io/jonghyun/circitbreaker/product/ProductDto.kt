package io.jonghyun.circitbreaker.product

data class ProductDto(
    val id: Long,
    val name: String,
    val status: String,
)