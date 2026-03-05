package io.jonghyun.Redis.caching

import io.jonghyun.Redis.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "products")
class Product(
    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var price: Long,
) : BaseEntity() {
    fun toDto() = ProductDto(id = id, name = name, price = price)
}