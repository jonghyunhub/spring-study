package io.jonghyun.Redis.benchmark

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class BenchmarkService(
    private val goodsRepository: GoodsRepository,
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    @Qualifier("coreDataSource") dataSource: DataSource,
) {
    private val jdbcTemplate = JdbcTemplate(dataSource)

    fun findByMysql(id: Long): GoodsDto =
        goodsRepository.findById(id)
            .orElseThrow { NoSuchElementException("benchmark product not found: $id") }
            .toDto()

    fun findByMysqlJdbc(id: Long): GoodsDto {
        val sql = "SELECT id, name, price, stock, category FROM goods WHERE id = ?"
        return jdbcTemplate.queryForObject(sql, { rs, _ ->
            GoodsDto(
                id = rs.getLong("id"),
                name = rs.getString("name"),
                price = rs.getLong("price"),
                stock = rs.getInt("stock"),
                category = rs.getString("category"),
            )
        }, id) ?: throw NoSuchElementException("benchmark product not found: $id")
    }

    fun findByRedisString(id: Long): GoodsDto {
        val json = stringRedisTemplate.opsForValue().get("goods:string:$id")
            ?: throw NoSuchElementException("goods:string:$id not found")
        return objectMapper.readValue(json, GoodsDto::class.java)
    }

    fun findByRedisHash(id: Long): GoodsDto {
        val fields = stringRedisTemplate.opsForHash<String, String>()
            .entries("goods:hash:$id")
        check(fields.isNotEmpty()) { "goods:hash:$id not found" }
        return GoodsDto(
            id = fields["id"]!!.toLong(),
            name = fields["name"]!!,
            price = fields["price"]!!.toLong(),
            stock = fields["stock"]!!.toInt(),
            category = fields["category"]!!,
        )
    }
}
