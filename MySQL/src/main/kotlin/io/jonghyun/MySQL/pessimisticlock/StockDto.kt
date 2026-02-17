package io.jonghyun.MySQL.pessimisticlock

import io.jonghyun.MySQL.domain.Stock

data class StockDto(
    val id: Long?,
    val productId: Long,
    val quantity: Int,
    val version: Long
) {
    companion object {
        fun from(stock: Stock): StockDto {
            return StockDto(
                id = stock.id,
                productId = stock.productId,
                quantity = stock.quantity,
                version = stock.version
            )
        }

        fun fromList(stocks: List<Stock>): List<StockDto> {
            return stocks.map { from(it) }
        }
    }
}