# Redis 캐싱 전략 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 상품 도메인을 이용해 Redis 캐싱 핵심 개념(히트/미스, TTL, 무효화, Cache-Aside vs Write-Through)을 `@Cacheable`과 `RedisTemplate` 두 방식으로 구현하고 테스트로 검증한다.

**Architecture:** Spring Cache 추상화(`@Cacheable`)와 `StringRedisTemplate` 직접 제어를 나란히 구현. 테스트 4개가 각 개념을 독립적으로 검증. 캐시에는 JPA 엔티티 대신 `ProductDto`를 저장해 직렬화 문제를 방지한다.

**Tech Stack:** Spring Boot (Kotlin), Spring Data Redis, `RedisCacheManager`, `StringRedisTemplate`, Jackson, JPA, JUnit 5, Mockito

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
