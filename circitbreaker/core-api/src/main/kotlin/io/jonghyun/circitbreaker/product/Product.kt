package io.jonghyun.circitbreaker.product

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "product")
data class Product(
    @Id
    val id: Long,
    val name: String,
    val status: String,
)