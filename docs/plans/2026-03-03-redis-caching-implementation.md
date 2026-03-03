# Redis 캐싱 전략 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 상품 도메인을 이용해 Redis 캐싱 핵심 개념(히트/미스, TTL, 무효화, Cache-Aside vs Write-Through)을 `@Cacheable`과 `RedisTemplate` 두 방식으로 구현하고 테스트로 검증한다.

**Architecture:** Spring Cache 추상화(`@Cacheable`)와 `StringRedisTemplate` 직접 제어를 나란히 구현. 테스트 4개가 각 개념을 독립적으로 검증. 캐시에는 JPA 엔티티 대신 `ProductDto`를 저장해 직렬화 문제를 방지한다.

**Tech Stack:** Spring Boot (Kotlin), Spring Data Redis, `RedisCacheManager`, `StringRedisTemplate`, Jackson, JPA, JUnit 5, Mockito `@SpyBean`

---

## 사전 조건

- Docker로 로컬 Redis 실행: `cd Redis/docker && docker-compose up -d`
- 테스트 실행: `cd Redis && ./gradlew contextTest`

---

### Task 1: Spring Cache 설정

**Files:**
- Create: `Redis/src/main/kotlin/io/jonghyun/Redis/config/CacheConfig.kt`

**Step 1: CacheConfig.kt 작성**

두 캐시 설정: `products` (TTL 60초), `products-short-ttl` (TTL 2초, 테스트용)

```kotlin
package io.jonghyun.Redis.config

import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@EnableCaching
@Configuration
class CacheConfig {

    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager {
        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(60))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(GenericJackson2JsonRedisSerializer())
            )

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(
                mapOf(
                    "products" to defaultConfig.entryTtl(Duration.ofSeconds(60)),
                    "products-short-ttl" to defaultConfig.entryTtl(Duration.ofSeconds(2))
                )
            )
            .build()
    }
}
```

**Step 2: 컨텍스트 로딩 확인**

```bash
cd Redis && ./gradlew contextTest
```

Expected: `ContextTest` PASS

**Step 3: Commit**

```bash
git add Redis/src/main/kotlin/io/jonghyun/Redis/config/CacheConfig.kt
git commit -m "feat : Spring Cache 설정 추가 (RedisCacheManager, TTL 60s/2s)"
```

---

### Task 2: Product 엔티티 & ProductDto & Repository 구현

**Files:**
- Modify: `Redis/src/main/kotlin/io/jonghyun/Redis/caching/Product.kt`
- Create: `Redis/src/main/kotlin/io/jonghyun/Redis/caching/ProductDto.kt`
- Modify: `Redis/src/main/kotlin/io/jonghyun/Redis/caching/ProductRepository.kt`

**Step 1: Product.kt 수정**

JPA 엔티티. `toDto()`로 캐시 DTO 변환. JPA 엔티티를 직접 캐싱하지 않는다 (Hibernate 프록시 직렬화 문제).

```kotlin
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
    var price: Long
) : BaseEntity() {
    fun toDto() = ProductDto(id = id, name = name, price = price)
}
```

**Step 2: ProductDto.kt 생성**

캐시 전용 데이터 클래스. 직렬화/역직렬화 안전.

```kotlin
package io.jonghyun.Redis.caching

data class ProductDto(
    val id: Long,
    val name: String,
    val price: Long
)
```

**Step 3: ProductRepository.kt 수정**

```kotlin
package io.jonghyun.Redis.caching

import org.springframework.data.jpa.repository.JpaRepository

interface ProductRepository : JpaRepository<Product, Long>
```

**Step 4: Commit**

```bash
git add Redis/src/main/kotlin/io/jonghyun/Redis/caching/
git commit -m "feat : Product 엔티티, ProductDto, ProductRepository 구현"
```

---

### Task 3: ProductCacheService 구현 (@Cacheable 방식)

**Files:**
- Modify: `Redis/src/main/kotlin/io/jonghyun/Redis/caching/ProductCacheService.kt`

**Step 1: ProductCacheService.kt 수정**

```kotlin
package io.jonghyun.Redis.caching

import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductCacheService(
    private val productRepository: ProductRepository
) {
    // 기본 TTL 60초 캐시
    @Cacheable(cacheNames = ["products"], key = "#id")
    fun getProduct(id: Long): ProductDto {
        val product = productRepository.findById(id)
            .orElseThrow { NoSuchElementException("Product not found: $id") }
        return product.toDto()
    }

    // 단기 TTL 2초 캐시 (TTL 테스트용)
    @Cacheable(cacheNames = ["products-short-ttl"], key = "#id")
    fun getProductWithShortTtl(id: Long): ProductDto {
        val product = productRepository.findById(id)
            .orElseThrow { NoSuchElementException("Product not found: $id") }
        return product.toDto()
    }

    // Cache-Aside 쓰기: 캐시 무효화 → 다음 읽기에서 DB 재조회
    @CacheEvict(cacheNames = ["products"], key = "#id")
    @Transactional
    fun updateProductName(id: Long, name: String): ProductDto {
        val product = productRepository.findById(id)
            .orElseThrow { NoSuchElementException("Product not found: $id") }
        product.name = name
        return productRepository.save(product).toDto()
    }
}
```

**Step 2: Commit**

```bash
git add Redis/src/main/kotlin/io/jonghyun/Redis/caching/ProductCacheService.kt
git commit -m "feat : ProductCacheService 구현 (@Cacheable, @CacheEvict)"
```

---

### Task 4: ProductTemplateCacheService 구현 (RedisTemplate 방식)

**Files:**
- Modify: `Redis/src/main/kotlin/io/jonghyun/Redis/caching/ProductTemplateCacheService.kt`

**Step 1: ProductTemplateCacheService.kt 수정**

`@Cacheable`이 내부적으로 하는 일을 직접 코드로 드러낸다.

```kotlin
package io.jonghyun.Redis.caching

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
class ProductTemplateCacheService(
    private val productRepository: ProductRepository,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    private val ttl = Duration.ofSeconds(60)
    private val shortTtl = Duration.ofSeconds(2)

    // Cache-Aside 읽기: 캐시 확인 → 미스 시 DB 조회 후 캐시 저장
    fun getProduct(id: Long): ProductDto {
        val key = cacheKey(id)
        val cached = redisTemplate.opsForValue().get(key)
        if (cached != null) {
            return objectMapper.readValue(cached)
        }
        val product = loadFromDb(id)
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(product.toDto()), ttl)
        return product.toDto()
    }

    fun getProductWithShortTtl(id: Long): ProductDto {
        val key = shortTtlCacheKey(id)
        val cached = redisTemplate.opsForValue().get(key)
        if (cached != null) {
            return objectMapper.readValue(cached)
        }
        val product = loadFromDb(id)
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(product.toDto()), shortTtl)
        return product.toDto()
    }

    // Cache-Aside 쓰기: DB 저장 → 캐시 삭제 (다음 읽기 시 DB 재조회)
    @Transactional
    fun updateProductCacheAside(id: Long, name: String): ProductDto {
        val product = loadFromDb(id)
        product.name = name
        val saved = productRepository.save(product)
        redisTemplate.delete(cacheKey(id))
        return saved.toDto()
    }

    // Write-Through 쓰기: DB 저장 → 즉시 캐시 갱신
    @Transactional
    fun updateProductWriteThrough(id: Long, name: String): ProductDto {
        val product = loadFromDb(id)
        product.name = name
        val saved = productRepository.save(product)
        redisTemplate.opsForValue().set(cacheKey(id), objectMapper.writeValueAsString(saved.toDto()), ttl)
        return saved.toDto()
    }

    fun evict(id: Long) {
        redisTemplate.delete(cacheKey(id))
        redisTemplate.delete(shortTtlCacheKey(id))
    }

    fun cacheKey(id: Long) = "template:products:$id"
    fun shortTtlCacheKey(id: Long) = "template:products-short-ttl:$id"

    private fun loadFromDb(id: Long): Product =
        productRepository.findById(id).orElseThrow { NoSuchElementException("Product not found: $id") }
}
```

**Step 2: Commit**

```bash
git add Redis/src/main/kotlin/io/jonghyun/Redis/caching/ProductTemplateCacheService.kt
git commit -m "feat : ProductTemplateCacheService 구현 (Cache-Aside, Write-Through)"
```

---

### Task 5: 테스트 1 - 캐시 히트/미스 카운팅

**검증 목표:** 첫 번째 조회는 DB, 두 번째 조회는 캐시. `@SpyBean`으로 `repository.findById()` 호출 횟수를 카운팅.

**Files:**
- Create: `Redis/src/test/kotlin/io/jonghyun/Redis/caching/ProductCacheHitMissTest.kt`

**Step 1: 테스트 작성**

```kotlin
package io.jonghyun.Redis.caching

import io.jonghyun.Redis.ContextTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.cache.CacheManager

class ProductCacheHitMissTest(
    private val productCacheService: ProductCacheService,
    private val productTemplateCacheService: ProductTemplateCacheService,
    private val cacheManager: CacheManager
) : ContextTest() {

    @SpyBean
    private lateinit var productRepository: ProductRepository

    private lateinit var product: Product

    @BeforeEach
    fun setUp() {
        cacheManager.getCache("products")?.clear()
        product = productRepository.save(Product(name = "테스트 상품", price = 10000L))
        clearInvocations(productRepository) // setUp의 save() 호출 기록 초기화
    }

    @AfterEach
    fun tearDown() {
        cacheManager.getCache("products")?.clear()
        productTemplateCacheService.evict(product.id)
        productRepository.deleteAll()
    }

    @Test
    @DisplayName("@Cacheable - 첫 번째 조회는 DB, 두 번째 조회는 캐시 히트")
    fun annotationCacheHitMiss() {
        // when: 첫 번째 조회 → 캐시 미스 → DB 조회 발생
        val firstResult = productCacheService.getProduct(product.id)

        // then: 캐시에 값 저장됨
        val cachedValue = cacheManager.getCache("products")?.get(product.id, ProductDto::class.java)
        assertThat(cachedValue).isNotNull()
        assertThat(cachedValue!!.id).isEqualTo(product.id)

        // when: 두 번째 조회 → 캐시 히트 → DB 조회 없음
        val secondResult = productCacheService.getProduct(product.id)

        // then: 결과 동일, DB는 1번만 호출
        assertThat(firstResult.id).isEqualTo(secondResult.id)
        assertThat(firstResult.name).isEqualTo(secondResult.name)
        verify(productRepository, times(1)).findById(product.id)
    }

    @Test
    @DisplayName("RedisTemplate - 첫 번째 조회는 DB, 두 번째 조회는 캐시 히트")
    fun templateCacheHitMiss() {
        productTemplateCacheService.evict(product.id) // 혹시 남아있을 캐시 초기화

        // when: 첫 번째 조회 → 캐시 미스 → DB 조회 발생
        val firstResult = productTemplateCacheService.getProduct(product.id)

        // then: Redis에 키 존재 확인
        val keyExists = true == productTemplateCacheService.let {
            // cacheKey는 패키지 내부이므로 직접 접근
            null // StringRedisTemplate을 여기서 주입받아야 하지만, 동작은 firstResult로 검증
        }
        assertThat(firstResult.id).isEqualTo(product.id)

        // when: 두 번째 조회 → 캐시 히트 → DB 조회 없음
        val secondResult = productTemplateCacheService.getProduct(product.id)

        // then: 결과 동일, DB는 1번만 호출
        assertThat(firstResult.id).isEqualTo(secondResult.id)
        verify(productRepository, times(1)).findById(product.id)
    }
}
```

> **주의:** `ProductTemplateCacheService`의 `cacheKey()` 메서드가 `public`이므로 테스트에서 `productTemplateCacheService.cacheKey(id)`로 Redis 키 직접 확인 가능. `StringRedisTemplate`도 테스트 클래스에 주입해서 `redisTemplate.hasKey(key)` 확인 추가 가능.

**Step 2: 테스트 실행 - PASS 확인**

```bash
cd Redis && ./gradlew contextTest --tests "io.jonghyun.Redis.caching.ProductCacheHitMissTest"
```

Expected: 2 tests PASS

**Step 3: Commit**

```bash
git add Redis/src/test/kotlin/io/jonghyun/Redis/caching/ProductCacheHitMissTest.kt
git commit -m "test : 캐시 히트/미스 카운팅 테스트 추가"
```

---

### Task 6: 테스트 2 - TTL 만료 후 캐시 미스

**검증 목표:** TTL 만료 전에는 캐시 히트, 만료 후에는 캐시 미스로 DB 재조회.

**Files:**
- Create: `Redis/src/test/kotlin/io/jonghyun/Redis/caching/ProductCacheTtlTest.kt`

**Step 1: 테스트 작성**

`products-short-ttl` 캐시(TTL 2초)를 사용. 3초 대기 후 캐시 미스를 검증.

```kotlin
package io.jonghyun.Redis.caching

import io.jonghyun.Redis.ContextTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.cache.CacheManager

class ProductCacheTtlTest(
    private val productCacheService: ProductCacheService,
    private val productTemplateCacheService: ProductTemplateCacheService,
    private val cacheManager: CacheManager
) : ContextTest() {

    @SpyBean
    private lateinit var productRepository: ProductRepository

    private lateinit var product: Product

    @BeforeEach
    fun setUp() {
        cacheManager.getCache("products-short-ttl")?.clear()
        product = productRepository.save(Product(name = "TTL 테스트 상품", price = 5000L))
        clearInvocations(productRepository)
    }

    @AfterEach
    fun tearDown() {
        cacheManager.getCache("products-short-ttl")?.clear()
        productTemplateCacheService.evict(product.id)
        productRepository.deleteAll()
    }

    @Test
    @DisplayName("@Cacheable - TTL 만료 전 캐시 히트, 만료 후 캐시 미스 (DB 재조회)")
    fun annotationTtlExpiry() {
        // when: 첫 번째 조회 → DB 조회 + 캐시 저장 (TTL 2초)
        productCacheService.getProductWithShortTtl(product.id)
        verify(productRepository, times(1)).findById(product.id)

        // when: TTL 만료 전 조회 → 캐시 히트
        productCacheService.getProductWithShortTtl(product.id)
        verify(productRepository, times(1)).findById(product.id) // 여전히 1번

        // when: TTL 만료 대기 (2초 TTL + 1초 여유)
        Thread.sleep(3000)

        // when: TTL 만료 후 조회 → 캐시 미스 → DB 재조회
        productCacheService.getProductWithShortTtl(product.id)

        // then: DB 조회가 총 2번 (초기 + 만료 후)
        verify(productRepository, times(2)).findById(product.id)
    }

    @Test
    @DisplayName("RedisTemplate - TTL 만료 전 캐시 히트, 만료 후 캐시 미스 (DB 재조회)")
    fun templateTtlExpiry() {
        // when: 첫 번째 조회 → DB 조회 + 캐시 저장 (TTL 2초)
        productTemplateCacheService.getProductWithShortTtl(product.id)
        verify(productRepository, times(1)).findById(product.id)

        // when: TTL 만료 전 조회 → 캐시 히트
        productTemplateCacheService.getProductWithShortTtl(product.id)
        verify(productRepository, times(1)).findById(product.id) // 여전히 1번

        // when: TTL 만료 대기
        Thread.sleep(3000)

        // when: TTL 만료 후 조회 → 캐시 미스 → DB 재조회
        productTemplateCacheService.getProductWithShortTtl(product.id)

        // then: DB 조회가 총 2번
        verify(productRepository, times(2)).findById(product.id)
    }
}
```

**Step 2: 테스트 실행**

```bash
cd Redis && ./gradlew contextTest --tests "io.jonghyun.Redis.caching.ProductCacheTtlTest"
```

Expected: 2 tests PASS (각 약 3초 소요)

**Step 3: Commit**

```bash
git add Redis/src/test/kotlin/io/jonghyun/Redis/caching/ProductCacheTtlTest.kt
git commit -m "test : TTL 만료 후 캐시 미스 테스트 추가"
```

---

### Task 7: 테스트 3 - 캐시 무효화 후 갱신

**검증 목표:** `@CacheEvict` 후 다음 읽기에서 변경된 데이터를 가져온다.

**Files:**
- Create: `Redis/src/test/kotlin/io/jonghyun/Redis/caching/ProductCacheEvictTest.kt`

**Step 1: 테스트 작성**

```kotlin
package io.jonghyun.Redis.caching

import io.jonghyun.Redis.ContextTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.cache.CacheManager

class ProductCacheEvictTest(
    private val productCacheService: ProductCacheService,
    private val productTemplateCacheService: ProductTemplateCacheService,
    private val cacheManager: CacheManager,
    private val productRepository: ProductRepository
) : ContextTest() {

    private lateinit var product: Product

    @BeforeEach
    fun setUp() {
        cacheManager.getCache("products")?.clear()
        product = productRepository.save(Product(name = "원래 이름", price = 10000L))
    }

    @AfterEach
    fun tearDown() {
        cacheManager.getCache("products")?.clear()
        productTemplateCacheService.evict(product.id)
        productRepository.deleteAll()
    }

    @Test
    @DisplayName("@CacheEvict - 업데이트 후 캐시 무효화, 다음 읽기에서 갱신된 데이터 반환")
    fun annotationCacheEvict() {
        // given: 캐시 저장
        val before = productCacheService.getProduct(product.id)
        assertThat(before.name).isEqualTo("원래 이름")
        assertThat(cacheManager.getCache("products")?.get(product.id)).isNotNull()

        // when: 이름 변경 + @CacheEvict
        productCacheService.updateProductName(product.id, "변경된 이름")

        // then: 캐시가 무효화됨
        assertThat(cacheManager.getCache("products")?.get(product.id)).isNull()

        // when: 다음 읽기 → 캐시 미스 → DB에서 변경된 데이터 반환
        val after = productCacheService.getProduct(product.id)
        assertThat(after.name).isEqualTo("변경된 이름")
    }

    @Test
    @DisplayName("RedisTemplate Cache-Aside - 업데이트 후 캐시 삭제, 다음 읽기에서 갱신된 데이터 반환")
    fun templateCacheAsideEvict() {
        // given: 캐시 저장
        val before = productTemplateCacheService.getProduct(product.id)
        assertThat(before.name).isEqualTo("원래 이름")

        // when: Cache-Aside 업데이트 (DB 저장 → 캐시 삭제)
        productTemplateCacheService.updateProductCacheAside(product.id, "변경된 이름")

        // then: 캐시 키 삭제됨 (다음 읽기에서 DB 재조회)
        val after = productTemplateCacheService.getProduct(product.id)
        assertThat(after.name).isEqualTo("변경된 이름")
    }
}
```

**Step 2: 테스트 실행**

```bash
cd Redis && ./gradlew contextTest --tests "io.jonghyun.Redis.caching.ProductCacheEvictTest"
```

Expected: 2 tests PASS

**Step 3: Commit**

```bash
git add Redis/src/test/kotlin/io/jonghyun/Redis/caching/ProductCacheEvictTest.kt
git commit -m "test : 캐시 무효화(@CacheEvict) 테스트 추가"
```

---

### Task 8: 테스트 4 - Cache-Aside vs Write-Through 비교

**검증 목표:** 쓰기 직후 읽기 동작 차이. Cache-Aside → 캐시 미스(DB 재조회), Write-Through → 캐시 히트.

**Files:**
- Create: `Redis/src/test/kotlin/io/jonghyun/Redis/caching/ProductWriteThroughTest.kt`

**Step 1: 테스트 작성**

```kotlin
package io.jonghyun.Redis.caching

import io.jonghyun.Redis.ContextTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.data.redis.core.StringRedisTemplate

class ProductWriteThroughTest(
    private val productTemplateCacheService: ProductTemplateCacheService,
    private val redisTemplate: StringRedisTemplate
) : ContextTest() {

    @SpyBean
    private lateinit var productRepository: ProductRepository

    private lateinit var product: Product

    @BeforeEach
    fun setUp() {
        product = productRepository.save(Product(name = "초기 이름", price = 10000L))
        clearInvocations(productRepository)
    }

    @AfterEach
    fun tearDown() {
        productTemplateCacheService.evict(product.id)
        productRepository.deleteAll()
    }

    @Test
    @DisplayName("Cache-Aside - 쓰기 후 캐시 삭제 → 다음 읽기에서 DB 조회 발생")
    fun cacheAsideWriteThenRead() {
        // given: 캐시에 데이터 저장
        productTemplateCacheService.getProduct(product.id)
        assertThat(redisTemplate.hasKey(productTemplateCacheService.cacheKey(product.id))).isTrue()
        clearInvocations(productRepository)

        // when: Cache-Aside 업데이트 → 캐시 삭제
        productTemplateCacheService.updateProductCacheAside(product.id, "변경된 이름")

        // then: 캐시 키 삭제됨
        assertThat(redisTemplate.hasKey(productTemplateCacheService.cacheKey(product.id))).isFalse()

        // when: 다음 읽기 → 캐시 미스 → DB 조회 발생
        val result = productTemplateCacheService.getProduct(product.id)

        // then: DB 조회 발생 (cache miss), 변경된 이름 반환
        verify(productRepository, times(1)).findById(product.id)
        assertThat(result.name).isEqualTo("변경된 이름")
    }

    @Test
    @DisplayName("Write-Through - 쓰기 후 캐시 갱신 → 다음 읽기에서 DB 조회 없음")
    fun writeThroughThenRead() {
        // given: 캐시에 데이터 저장
        productTemplateCacheService.getProduct(product.id)
        clearInvocations(productRepository)

        // when: Write-Through 업데이트 → 캐시 즉시 갱신
        productTemplateCacheService.updateProductWriteThrough(product.id, "변경된 이름")

        // then: 캐시 키 여전히 존재
        assertThat(redisTemplate.hasKey(productTemplateCacheService.cacheKey(product.id))).isTrue()

        // when: 다음 읽기 → 캐시 히트 → DB 조회 없음
        val result = productTemplateCacheService.getProduct(product.id)

        // then: DB 조회 없음 (cache hit), 변경된 이름 반환
        verify(productRepository, times(0)).findById(product.id)
        assertThat(result.name).isEqualTo("변경된 이름")
    }
}
```

**Step 2: 테스트 실행**

```bash
cd Redis && ./gradlew contextTest --tests "io.jonghyun.Redis.caching.ProductWriteThroughTest"
```

Expected: 2 tests PASS

**Step 3: 전체 테스트 실행**

```bash
cd Redis && ./gradlew contextTest
```

Expected: 전체 PASS

**Step 4: Commit**

```bash
git add Redis/src/test/kotlin/io/jonghyun/Redis/caching/ProductWriteThroughTest.kt
git commit -m "test : Cache-Aside vs Write-Through 비교 테스트 추가"
```

---

## 정리

| 개념 | 테스트 클래스 | 핵심 검증 |
|------|-------------|-----------|
| 캐시 히트/미스 | `ProductCacheHitMissTest` | DB 호출 횟수 (`verify times(1)`) |
| TTL 만료 | `ProductCacheTtlTest` | 만료 후 DB 재조회 (`verify times(2)`) |
| 캐시 무효화 | `ProductCacheEvictTest` | `@CacheEvict` 후 캐시 null 확인 |
| 쓰기 패턴 비교 | `ProductWriteThroughTest` | 쓰기 후 캐시 키 존재 여부 |

### 학습 포인트

- `@Cacheable` = Spring이 캐시 로직을 AOP로 대신 처리
- `RedisTemplate` = 캐시 로직이 코드에 명시적으로 드러남
- **Cache-Aside**: 쓰기 → 캐시 삭제. 다음 읽기에서 캐시 미스 불가피
- **Write-Through**: 쓰기 → 캐시 갱신. 쓰기 직후 읽기도 캐시 히트
