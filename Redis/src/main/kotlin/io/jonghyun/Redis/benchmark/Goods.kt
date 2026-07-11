package io.jonghyun.Redis.benchmark

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "goods")
class Goods(
    @Id
    val id: Long,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val price: Long,

    @Column(nullable = false)
    val stock: Int,

    @Column(nullable = false)
    val category: String,

    @Column(nullable = false)
    val updatedAt: LocalDateTime,
) {
    fun toDto() = GoodsDto(
        id = id,
        name = name,
        price = price,
        stock = stock,
        category = category,
    )
}
