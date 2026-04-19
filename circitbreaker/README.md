# Resilience4j Circuit Breaker 학습 프로젝트

Resilience4j 기반 Circuit Breaker의 동작을 직접 실행하고 Grafana 대시보드로 확인하는 학습 저장소입니다.

---

## 프로젝트 구조

```
circitbreaker/
├── core-api/          # Circuit Breaker 적용 앱 (port 8080)
├── stub-service/      # 외부 서비스 장애 시뮬레이터 (port 8081)
└── docker/            # Prometheus + Grafana 모니터링 환경
```

### 모듈 역할

| 모듈 | 포트 | 역할 |
|---|---|---|
| `core-api` | 8080 | `@CircuitBreaker` 적용. stub-service를 Feign으로 호출 |
| `stub-service` | 8081 | 제어 API로 정상/실패/지연 응답을 전환하는 시뮬레이터 |
| Prometheus | 9090 | core-api `/actuator/prometheus` 5초마다 스크랩 |
| Grafana | 3000 | Circuit Breaker 상태·호출 현황 대시보드 |

---

## Circuit Breaker 설정값 (`core-api/src/main/resources/application.yml`)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      stub-service:
        sliding-window-type: COUNT_BASED          # 최근 N건 기준으로 실패율 계산
        sliding-window-size: 10                   # 판단 기준 건수
        minimum-number-of-calls: 5               # 최소 5건 쌓여야 실패율 판단 시작
        failure-rate-threshold: 50               # 실패율 50% 초과 → OPEN
        wait-duration-in-open-state: 10s         # OPEN 유지 시간 → HALF_OPEN 전환
        permitted-number-of-calls-in-half-open-state: 3  # HALF_OPEN에서 테스트 요청 수
        slow-call-duration-threshold: 2s         # 2초 이상 = slow call
        slow-call-rate-threshold: 50             # slow call 50% 초과 → OPEN
```

### 상태 전환 조건 요약

```
CLOSED ──실패율 > 50%──▶ OPEN ──10초 경과──▶ HALF_OPEN
                                                   │
                         CLOSED ◀──3건 성공 기준──┤
                         OPEN   ◀──3건 실패 기준──┘
```

---

## 실행 방법

### 1단계: 모니터링 환경 실행

```bash
cd circitbreaker/docker
docker-compose up -d
```

| 접속 주소 | 설명 |
|---|---|
| http://localhost:3000 | Grafana (admin / admin) |
| http://localhost:9090 | Prometheus |

Grafana 접속 후 **Dashboards → Circuit Breaker Dashboard** 를 열면 자동으로 등록되어 있습니다.

### 2단계: stub-service 실행

```bash
./gradlew :stub-service:bootRun
```

### 3단계: core-api 실행

```bash
./gradlew :core-api:bootRun
```

---

## 테스트 수행 구조

### 호출 흐름

```
클라이언트
    │
    │  GET /products/{id}
    ▼
ProductController (core-api:8080)
    │
    ▼
ProductService
    │  @CircuitBreaker(name = "stub-service", fallbackMethod = "getProductFallback")
    │
    ├─[CLOSED]────▶ StubServiceClient (Feign)
    │                   │
    │                   │  GET /external/products/{id}
    │                   ▼
    │               stub-service:8081
    │                   │
    │               [NORMAL] 200 OK
    │               [FAIL]   500 Internal Server Error   ─▶ FeignException 발생
    │               [SLOW]   3초 지연 후 200 OK          ─▶ slow call 카운트
    │
    ├─[OPEN]─────▶ getProductFallback(id, CallNotPermittedException)
    │                   └─▶ 503 Service Unavailable (stub-service 호출 없음)
    │
    └─[실패]─────▶ getProductFallback(id, Exception)
                        └─▶ 502 Bad Gateway
```

### 시나리오별 테스트

#### 시나리오 1: CLOSED → OPEN 전환 (실패율 기반)

```bash
# 1. 실패 모드 전환
curl -X POST localhost:8081/control/fail

# 2. 반복 호출 (5건 이상 — minimum-number-of-calls 충족)
for i in {1..10}; do curl -s localhost:8080/products/1; echo; done

# 3. 결과 확인
# 처음 5건: 502 Bad Gateway  (stub-service가 500 반환 → 실패로 카운트)
# 이후 건:  503 Service Unavailable  (CB OPEN → 호출 차단)

# 4. CB 상태 확인
curl localhost:8080/actuator/health | jq '.components.circuitBreakers'
```

#### 시나리오 2: OPEN → HALF_OPEN → CLOSED 복구

```bash
# 1. CB가 OPEN인 상태에서 stub-service 정상화
curl -X POST localhost:8081/control/recover

# 2. 10초 대기 (wait-duration-in-open-state)

# 3. HALF_OPEN 상태에서 3건 요청 (permitted-number-of-calls-in-half-open-state)
for i in {1..3}; do curl -s localhost:8080/products/1; echo; done

# 4. 3건 모두 성공 → CLOSED 복구
curl localhost:8080/actuator/health | jq '.components.circuitBreakers'
```

#### 시나리오 3: CLOSED → OPEN 전환 (slow call 기반)

```bash
# 1. 느린 응답 모드 전환 (3초 지연)
curl -X POST localhost:8081/control/slow

# 2. 반복 호출
for i in {1..10}; do curl -s localhost:8080/products/1; echo; done

# slow-call-duration-threshold(2s) 초과 → slow call로 카운트
# slow-call-rate-threshold(50%) 초과 → CB OPEN
```

#### 시나리오 4: 현재 상태 확인 API

```bash
# CB 인스턴스 목록 및 상태
curl localhost:8080/actuator/circuitbreakers | jq

# CB 이벤트 로그 (상태 전환 기록)
curl localhost:8080/actuator/circuitbreakerevents | jq

# stub-service 현재 모드
curl localhost:8081/control/status
```

---

## Grafana 대시보드 패널 설명

| 패널 | 설명 |
|---|---|
| **Circuit Breaker 상태** | CLOSED(초록) / OPEN(빨강) / HALF_OPEN(노랑) 즉시 확인 |
| **실패율 %** | 50% 임계값 도달 전 미리 확인 가능 |
| **느린 호출 비율 %** | slow call로 OPEN 되는 케이스 추적 |
| **슬라이딩 윈도우 버퍼** | 최근 10건 중 성공/실패 건수 |
| **호출 종류별 처리량** | `not_permitted` 급증 = CB OPEN 상태 |
| **HTTP 요청 처리량** | 503 증가 = CB가 클라이언트 요청을 차단 중 |
| **CB 상태 변화 이력** | CLOSED→OPEN→HALF_OPEN 전환 타임라인 |

---

## stub-service 제어 API

| 메서드 | 경로 | 동작 |
|---|---|---|
| `POST` | `/control/fail` | 이후 모든 요청에 **500** 반환 |
| `POST` | `/control/slow` | 이후 모든 요청에 **3초 지연** 후 200 반환 |
| `POST` | `/control/recover` | **정상** 응답으로 복구 |
| `GET` | `/control/status` | 현재 모드 확인 |
| `GET` | `/external/products/{id}` | core-api가 Feign으로 호출하는 실제 엔드포인트 |

---

## k6 부하 테스트

실제 트래픽을 넣으면서 Grafana 대시보드로 CB 상태 전환을 실시간 확인합니다.

### 설치

```bash
brew install k6
```

### 파일 구조

```
k6/
├── lib/
│   └── helpers.js          # 공통 함수 (stub 모드 전환, 응답 체크, CB 상태 조회)
└── scenarios/
    ├── 01-failure-rate.js  # 실패율 기반 OPEN 전환
    ├── 02-slow-call.js     # slow call 기반 OPEN 전환
    └── 03-recovery.js      # OPEN → HALF_OPEN → CLOSED 복구 흐름
```

### 시나리오 1: 실패율 기반 OPEN 전환

```
0s        5s              35s      40s
├─ramp-up─┤───── 5 VU ────┤─down───┤
          stub=FAIL → 반복 호출
          502 × 5건 → CB OPEN → 503 차단
```

```bash
k6 run k6/scenarios/01-failure-rate.js
```

**Grafana에서 확인할 것**
- 실패율 게이지가 50%를 넘는 순간 CB 상태가 OPEN(빨강)으로 전환
- `not_permitted` 호출 급증

### 시나리오 2: slow call 기반 OPEN 전환

```
0s        5s                     65s   70s
├─ramp-up─┤──── 3 VU (3초 지연) ──┤─down─┤
          stub=SLOW → 2초 임계값 초과 카운트
          slow call 비율 50% → CB OPEN
```

```bash
k6 run k6/scenarios/02-slow-call.js
```

**Grafana에서 확인할 것**
- 느린 호출 비율 게이지 상승
- 응답시간 p95 > 2000ms 구간에서 CB OPEN 전환

### 시나리오 3: OPEN → HALF_OPEN → CLOSED 복구 흐름

```
0s          20s  21s     31s   35s          55s
├─phase-1───┤    │        │    ├─phase-3────┤
  5VU/FAIL       │        │      3VU/recover 후 정상 응답 확인
             [switch]  [phase-2]
           recover 전환  2VU/OPEN 상태 503 확인
                        (10s 대기 = wait-duration)
```

```bash
k6 run k6/scenarios/03-recovery.js
```

**Grafana에서 확인할 것**
- CB 상태 변화 이력 패널: CLOSED → OPEN → HALF_OPEN → CLOSED 전환 타임라인
- phase-3 시작 시점부터 200 응답 복귀

### 결과 해석

| 응답 코드 | 의미 | CB 상태 |
|---|---|---|
| 200 | 정상 | CLOSED 또는 HALF_OPEN 복구 중 |
| 502 | 외부 서비스 실패 (실패 카운트 중) | CLOSED |
| 503 | CB가 호출 자체를 차단 | OPEN |

---

## 학습 체크리스트

- [ ] CLOSED → OPEN 전환 (실패율 기반) 직접 확인
- [ ] CLOSED → OPEN 전환 (slow call 기반) 직접 확인
- [ ] OPEN → HALF_OPEN → CLOSED 복구 흐름 확인
- [ ] OPEN 상태에서 `not_permitted` 카운트 증가 Grafana로 확인
- [ ] `CallNotPermittedException` fallback과 `Exception` fallback 분기 차이 이해
- [ ] COUNT_BASED vs TIME_BASED sliding window 차이 실험
