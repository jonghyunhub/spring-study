package io.jonghyun.Redis.benchmark

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import javax.sql.DataSource

@Service
class SeedService(
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    @Qualifier("coreDataSource") dataSource: DataSource,
) {
    private val jdbcTemplate = JdbcTemplate(dataSource)
    private val serializer = StringRedisSerializer()
    private val categories = listOf("A", "B", "C", "D", "E")

    fun seed(count: Int) {
        truncateMysql()
        flushRedisKeys()
        seedMysql(count)
        seedRedisString(count)
        seedRedisHash(count)
    }

    private fun truncateMysql() {
        jdbcTemplate.execute("DELETE FROM goods")
    }

    private fun flushRedisKeys() {
        val keys = stringRedisTemplate.keys("goods:*")
        if (keys.isNotEmpty()) stringRedisTemplate.delete(keys)
    }

    private fun seedMysql(count: Int) {
        val sql = """
            INSERT INTO goods (id, name, price, stock, category, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        val now = LocalDateTime.now()

        (1..count).chunked(500).forEach { chunk ->
            jdbcTemplate.batchUpdate(sql, chunk.map { i ->
                arrayOf(i.toLong(), "product-$i", (i * 100L), i % 100, categories[i % 5], now)
            })
        }
    }

    private fun seedRedisString(count: Int) {
        (1..count).chunked(1000).forEach { chunk ->
            stringRedisTemplate.executePipelined(object : RedisCallback<Any?> {
                override fun doInRedis(connection: RedisConnection): Any? {
                    chunk.forEach { i ->
                        val dto = GoodsDto(
                            id = i.toLong(),
                            name = "product-$i",
                            price = i * 100L,
                            stock = i % 100,
                            category = categories[i % 5],
                        )
                        connection.stringCommands().set(
                            serializer.serialize("goods:string:$i")!!,
                            serializer.serialize(objectMapper.writeValueAsString(dto))!!,
                        )
                    }
                    return null
                }
            })
        }
    }

    private fun seedRedisHash(count: Int) {
        (1..count).chunked(1000).forEach { chunk ->
            stringRedisTemplate.executePipelined(object : RedisCallback<Any?> {
                override fun doInRedis(connection: RedisConnection): Any? {
                    chunk.forEach { i ->
                        val hashMap = mapOf(
                            serializer.serialize("id")!! to serializer.serialize(i.toString())!!,
                            serializer.serialize("name")!! to serializer.serialize("product-$i")!!,
                            serializer.serialize("price")!! to serializer.serialize((i * 100L).toString())!!,
                            serializer.serialize("stock")!! to serializer.serialize((i % 100).toString())!!,
                            serializer.serialize("category")!! to serializer.serialize(categories[i % 5])!!,
                        )
                        connection.hashCommands().hMSet(
                            serializer.serialize("goods:hash:$i")!!,
                            hashMap,
                        )
                    }
                    return null
                }
            })
        }
    }
}
