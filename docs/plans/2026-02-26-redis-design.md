# Redis 학습 설계

## 개요

Redis의 핵심 기능을 범용적으로 이해하기 위한 학습 설계.
각 주제는 **원리 검증 → 패턴 구현** 2단계로 진행한다.

## 학습 순서

복잡도 오름차순으로 진행한다.

```
캐싱 → 세션 → 랭킹 → Rate Limiting → 분산 락
```

## 패키지 구조

```
src/main/kotlin/io/jonghyun/Redis/
├── caching/          # 캐싱 전략
├── session/          # 세션 관리
├── ranking/          # 랭킹 (Sorted Set)
├── ratelimit/        # Rate Limiting
└── distributedlock/  # 분산 락

src/test/kotlin/io/jonghyun/Redis/
├── caching/
├── session/
├── ranking/
├── ratelimit/
└── distributedlock/
```

## 의존성 추가

- `spring-session-data-redis` — 세션 주제에서 필요

---

## 1. 캐싱 전략 (Caching)

### 원리 검증
- 캐시 히트 vs 캐시 미스 동작 확인
- TTL 만료 후 캐시 미스로 전환되는 흐름
- 캐시 무효화(`@CacheEvict`) 후 데이터 갱신 확인

### 패턴 구현
- Cache-Aside 패턴: `@Cacheable` / `@CacheEvict`
- 수동 캐시 제어: `RedisTemplate` 직접 사용

---

## 2. 세션 관리 (Session)

### 원리 검증
- 세션 생성 → Redis 저장 → TTL 만료 → 세션 소멸 흐름
- 동일 세션을 여러 서버 인스턴스에서 공유하는 동작

### 패턴 구현
- `spring-session-data-redis`를 이용한 세션 저장소 교체
- 세션 직렬화 방식(JDK vs JSON) 비교

---

## 3. 랭킹 (Sorted Set)

### 원리 검증
- `ZADD` / `ZINCRBY` / `ZRANGE` / `ZREVRANK` 동작
- 동점자 처리 방식 (score 동일 시 사전순 정렬)

### 패턴 구현
- 점수 기반 실시간 랭킹 집계
- 상위 N개 조회 / 특정 항목의 순위 조회

---

## 4. Rate Limiting

### 원리 검증
- 고정 윈도우(Fixed Window) vs 슬라이딩 윈도우(Sliding Window) 차이
- 경계 조건: 윈도우 전환 시점에서 버스트 허용 여부

### 패턴 구현
- Lua Script를 이용한 원자적 Rate Limiter 구현
- 요청 허용 / 차단 분기 처리

---

## 5. 분산 락 (Distributed Lock)

### 원리 검증
- `SETNX` 기반 락 vs Redisson `RLock` 동작 차이
- 락 경쟁 시나리오: 동시 요청 중 하나만 성공하는 흐름
- 락 만료 시 자동 해제 확인

### 패턴 구현
- Redisson `RLock`으로 임계 구역 보호
- AOP 기반 분산 락 추상화 (`@DistributedLock`)
