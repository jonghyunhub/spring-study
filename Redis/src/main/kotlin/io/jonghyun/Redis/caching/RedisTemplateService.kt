package io.jonghyun.Redis.caching

import com.fasterxml.jackson.databind.ObjectMapper
import io.jonghyun.Redis.product.ProductDto
import io.jonghyun.Redis.product.ProductRepository
import io.jonghyun.Redis.product.RedisTemplateInfo
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * RedisTemplate을 학습하기 위해 작성한 서비스
 */
@Service
class RedisTemplateService(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper,
    private val productRepository: ProductRepository,
) {


    /**
     * RedisTemplate 내부 속성 확인
     */
    fun inspectRedisTemplate(): RedisTemplateInfo {
        val keySerializer = redisTemplate.keySerializer
        val valueSerializer = redisTemplate.valueSerializer
        val hashKeySerializer = redisTemplate.hashKeySerializer
        val hashValueSerializer = redisTemplate.hashValueSerializer

        val valueOps = redisTemplate.opsForValue()
        val hashOps = redisTemplate.opsForHash<Any, Any>()

        return RedisTemplateInfo(
            keySerializer = keySerializer.javaClass.name ?: "null",
            valueSerializer = valueSerializer.javaClass.name ?: "null",
            hashKeySerializer = hashKeySerializer.javaClass.name ?: "null",
            hashValueSerializer = hashValueSerializer.javaClass.name ?: "null",
            valueOpsClassName = valueOps.javaClass.name,
            hashOpsClassName = hashOps.javaClass.name,
        )
    }


    /**
     * redisTemplate 을 통한 cache write
     */
    fun saveProductInCache(name: String) {
        val productDto = ProductDto(id = 0, name = name, price = 10000L)
        val productKey = cacheKey(name = productDto.name)
        val opsForValue = redisTemplate.opsForValue()
        opsForValue.set(productKey, productDto)
    }

    /**
     * redisTemplate 을 통한 cache read
     */
    fun getProductInCache(name: String): ProductDto? {
        val productKey = cacheKey(name = name)
        val opsForValue = redisTemplate.opsForValue()
        return opsForValue.get(productKey) as ProductDto?
    }


    /**
     * redisTemplate 을 통한 cache write with ttl
     */
    fun saveProductWithTtl(name: String, ttlSeconds: Long) {
        val productDto = ProductDto(id = 0, name = name, price = 10000L)
        val cacheKey = cacheKey(name = productDto.name)
        val opsForValue = redisTemplate.opsForValue()
        opsForValue.set(cacheKey, productDto, ttlSeconds, TimeUnit.SECONDS)
    }

    /**
     * redisTemplate 을 통한 명시적 캐시 삭제
     */
    fun evictProductInCache(name: String) {
        val cacheKey = cacheKey(name = name)
        redisTemplate.delete(cacheKey)
    }

    /**
     * redisTemplate 을 통한 bulk insert(collection 자료구조 기반으로 set 가능)
     */
    fun saveProductsInCache(name: String) {
        val opsForValue = redisTemplate.opsForValue()
        for (i in 0L..10L) {
            val productDto = ProductDto(id = i, name = name, price = 10000L)
            val productKey = cacheKey(name = productDto.name + "_$i")
            opsForValue.set(productKey, productDto)
        }
    }


    /**
     * redisTemplate 을 통한 bulk read(collection 자료구조 기반으로 get 가능) => mget 명령어 사용
     */
    fun getProductsInCache(name: String): List<ProductDto> {
        val opsForValue = redisTemplate.opsForValue()
        val cacheKeys = mutableListOf<String>()
        (0L..10L).forEach { i ->
            cacheKeys.add(cacheKey(name = name + "_$i"))
        }
        return opsForValue.multiGet(cacheKeys)
            ?.filterNotNull()
            ?.map { objectMapper.convertValue(it, ProductDto::class.java) }
            ?: emptyList()
    }

    /**
     * redisTemplate 을 통한 hash write
     */
    fun saveProductHash(name : String) {
        val productDto = ProductDto(id = 0, name = name, price = 10000L)
        val cacheKey = cacheKey(name = productDto.name)
        val opsForHash = redisTemplate.opsForHash<String, Any>()
        opsForHash.putAll(cacheKey, mapOf(
            "id" to  productDto.id,
            "name" to productDto.name,
            "price" to productDto.price
        ))
    }

    /**
     * redisTemplate 을 통한 hash key 기반 read
     */
    fun findProductHash(name: String): ProductDto {
        val cacheKey = cacheKey(name = name)
        val opsForHash = redisTemplate.opsForHash<String, Any>()
        val entries = opsForHash.entries(cacheKey)
        if (entries.isEmpty()) throw IllegalStateException("No product hash found for name $name")
        return objectMapper.convertValue(entries, ProductDto::class.java)
    }

    /**
     * redisTemplate 을 통한 hash update
     */
    fun updateProductPriceInHash(name: String, price: Long) : ProductDto{
        val cacheKey = cacheKey(name = name)
        val opsForHash = redisTemplate.opsForHash<String, Any>()

        // read
        val entries = opsForHash.entries(cacheKey)
        if (entries.isEmpty()) throw IllegalStateException("No product hash found for name $name")
        val readData = objectMapper.convertValue(entries, ProductDto::class.java)

        // update & save
        val productDto = ProductDto(id = readData.id, name = readData.name, price = price)
        opsForHash.putAll(cacheKey, mapOf(
            "id" to  productDto.id,
            "name" to productDto.name,
            "price" to productDto.price
        ))
        return productDto
    }

    /**
     * redisTemplate 을 통한 hash value 기반 read
     */
    fun findProductPriceFromHash(name: String): Int {
        val cacheKey = cacheKey(name = name)
        val opsForHash = redisTemplate.opsForHash<String, Any>()

        // read
        val result = opsForHash.get(cacheKey, "price") as Int?
            ?: throw IllegalStateException("No product hash found for name $name")
        return result
    }


    /**
     * RedisTemplate 사용 X 대신, RedisConnection 사용하여 cache write
     */
    fun saveRawProduct(name: String) {
        val cacheKey = cacheKey(name)
        val productDto = ProductDto(id = 0, name = name, price = 10000L)
        val jsonString = objectMapper.writeValueAsString(productDto)

        redisTemplate.execute { connection ->
            val byteKey = cacheKey.toByteArray(Charsets.UTF_8)
            val byteValue = jsonString.toByteArray(Charsets.UTF_8)

            connection.stringCommands().set(byteKey, byteValue)
        }
    }

    /**
     * RedisTemplate 사용 X 대신, RedisConnection 사용하여 cache 객체 read
     */
    fun findRawProduct(name: String): ProductDto {
        val cacheKey = cacheKey(name)

        // result는 byteArr 타입
        val result = redisTemplate.execute { connection ->
            val byteKey = cacheKey.toByteArray(Charsets.UTF_8)
            connection.stringCommands().get(byteKey)
        }

        if(result == null) throw IllegalStateException("No product hash found for name $name")
        return objectMapper.readValue(result, ProductDto::class.java)
    }

    /**
     * RedisTemplate 사용 X 대신, RedisConnection 사용하여 cache byte 배열 read
     */
    fun findRawBytes(name: String): ByteArray {
        val cacheKey = cacheKey(name)

        // result는 byteArr 타입
        val result = redisTemplate.execute { connection ->
            val byteKey = cacheKey.toByteArray(Charsets.UTF_8)
            connection.stringCommands().get(byteKey)
        }

        if(result == null) throw IllegalStateException("No product hash found for name $name")
        return result
    }


    private fun cacheKey(name: String) = "products:$name"
}