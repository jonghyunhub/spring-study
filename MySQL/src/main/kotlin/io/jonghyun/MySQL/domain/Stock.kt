package io.jonghyun.MySQL.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(name = "stock")
class Stock(
    @Column(nullable = false)
    val productId: Long,

    @Column(nullable = false)
    var quantity: Int,

    @Version
    var version: Long = 0
) : BaseEntity() {

    fun decrease(amount: Int) {
        if (quantity < amount) {
            throw IllegalArgumentException("재고가 부족합니다. 현재: $quantity, 요청: $amount")
        }
        quantity -= amount
    }

    fun increase(amount: Int) {
        quantity += amount
    }
}