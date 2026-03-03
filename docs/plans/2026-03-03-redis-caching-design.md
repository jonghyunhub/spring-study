# Redis 캐싱 전략 실습 설계

## 개요

상품 조회(Product) 도메인을 이용해 Redis 캐싱 전략의 핵심 개념을 검증한다.
`@Cacheable` 애노테이션 방식과 `RedisTemplate` 직접 제어 방식을 비교하며, 각 개념을 독립된 테스트로 분리해 학습한다.

---

## 패키지 구조

```
src/main/kotlin/io/jonghyun/Redis/
└── caching/
    ├── Product.kt                      # 도메인 엔티티 (id, name, price)
    ├── ProductRepository.kt            # JPA Repository
    ├── ProductCacheService.kt          # @Cacheable 기반 서비스
    └── ProductTemplateCacheService.kt  # RedisTemplate 기반 서비스

src/test/kotlin/io/jonghyun/Redis/
└── caching/
    ├── ProductCacheHitMissTest.kt      # 캐시 히트/미스 카운팅 검증
    ├── ProductCacheTtlTest.kt          # TTL 만료 후 미스 전환 검증
    ├── ProductCacheEvictTest.kt        # 무효화 후 갱신 검증
    └── ProductWriteThroughTest.kt      # Cache-Aside vs Write-Through 비교
```

---

## 검증 시나리오

### 1. 캐시 히트/미스 카운팅 (`ProductCacheHitMissTest`)

```
1. DB에 Product 저장
2. 첫 번째 조회 → 캐시 미스 → DB 쿼리 발생 (쿼리 카운트 1)
3. 두 번째 조회 → 캐시 히트 → DB 쿼리 없음 (쿼리 카운트 여전히 1)
```

- `@Cacheable` 버전과 `RedisTemplate` 버전 동일 동작 확인

---

### 2. TTL 만료 후 미스 전환 (`ProductCacheTtlTest`)

```
1. TTL=2초로 캐시 저장
2. 즉시 조회 → 캐시 히트
3. 3초 대기
4. 다시 조회 → 캐시 미스 → DB 재조회
```

- TTL 만료 후 자동으로 DB에서 재조회하는 흐름 검증

---

### 3. 무효화 후 갱신 — Cache-Aside (`ProductCacheEvictTest`)

```
1. Product 조회 → 캐시 저장
2. Product 이름 변경 + @CacheEvict 호출
3. 다시 조회 → 캐시 미스 → 변경된 데이터 조회됨
```

- 쓰기 시 캐시 무효화, 다음 읽기에서 최신 데이터 반영 검증

---

### 4. Cache-Aside vs Write-Through 비교 (`ProductWriteThroughTest`)

| 동작 | Cache-Aside | Write-Through |
|------|-------------|---------------|
| 쓰기 | DB 저장 → 캐시 삭제 | DB 저장 → 즉시 캐시 갱신 |
| 쓰기 직후 읽기 | 캐시 미스 → DB 조회 | 캐시 히트 |
| 구현 방식 | RedisTemplate 수동 제어 | RedisTemplate 수동 제어 |

- 쓰기 직후 조회 시 두 패턴의 동작 차이 검증

---

## 기술 스택

- Spring Boot + Kotlin
- Spring Data Redis (`RedisTemplate`, `@Cacheable`)
- Docker Compose (Redis 컨테이너)
- JUnit 5 + Testcontainers (또는 로컬 Redis)
