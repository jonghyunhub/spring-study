# Redis Streams 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | TYPE 명령어 구현하기 | ⬤○○ 쉬움 |
| 2 | 스트림 생성하기 | ⬤⬤○ 보통 |
| **3** | **엔트리 ID 유효성 검증** | ⬤○○ 쉬움 |
| 4 | 부분 자동 생성 ID | ⬤⬤○ 보통 |
| 5 | 완전 자동 생성 ID | ⬤⬤○ 보통 |
| 6 | 스트림에서 엔트리 조회하기 | ⬤⬤○ 보통 |
| 7 | `-` 를 사용한 쿼리 | ⬤○○ 쉬움 |
| 8 | `+` 를 사용한 쿼리 | ⬤○○ 쉬움 |
| 9 | XREAD로 단일 스트림 조회 | ⬤⬤○ 보통 |
| 10 | XREAD로 다중 스트림 조회 | ⬤⬤○ 보통 |
| 11 | 블로킹 읽기 | ⬤⬤⬤ 어려움 |
| 12 | 타임아웃 없는 블로킹 읽기 | ⬤⬤○ 보통 |
| 13 | `$` 를 사용한 블로킹 읽기 | ⬤○○ 쉬움 |

---

## Stage 3: 엔트리 ID 유효성 검증

### 🎯 목표

`XADD` 명령어에 **ID 유효성 검증 로직**을 추가합니다. Redis Stream의 엔트리 ID는 항상 증가해야 합니다.

---

### 📚 배경 지식

#### ID 순서 규칙

Redis Stream에서 새 엔트리의 ID는 반드시 **기존 엔트리들의 ID보다 커야** 합니다.

ID 비교는 다음 순서로 이루어집니다:
1. 먼저 **밀리초 타임스탬프**를 비교
2. 타임스탬프가 같으면 **시퀀스 번호**를 비교

```
1-1 < 1-2 < 2-0 < 2-1 < 10-0
```

#### 최소 ID 제한

`0-0`은 **최소 ID로 예약**되어 있어 사용할 수 없습니다.

#### 에러 메시지

| 상황 | 에러 메시지 |
|------|-------------|
| `0-0` 사용 시도 | `ERR The ID specified in XADD must be greater than 0-0` |
| 기존 ID보다 작거나 같음 | `ERR The ID specified in XADD is equal or smaller than the target stream top item` |

---

### ✅ 통과 조건

- `0-0` ID로 엔트리 추가 시 적절한 에러를 반환해야 합니다
- 마지막 엔트리 ID보다 작거나 같은 ID로 추가 시 에러를 반환해야 합니다
- 유효한 ID로 추가 시 정상적으로 엔트리가 추가되어야 합니다

---

### 💡 힌트

에러 응답은 **Simple Error** 형식으로 반환합니다:

```
-ERR 에러메시지\r\n
```

ID 비교 함수를 구현하세요:

```kotlin
data class StreamId(val milliseconds: Long, val sequence: Long) : Comparable<StreamId> {
    
    override fun compareTo(other: StreamId): Int {
        val msCompare = milliseconds.compareTo(other.milliseconds)
        return if (msCompare != 0) msCompare else sequence.compareTo(other.sequence)
    }
    
    companion object {
        fun parse(id: String): StreamId {
            val parts = id.split("-")
            return StreamId(parts[0].toLong(), parts[1].toLong())
        }
        
        val MIN = StreamId(0, 0)
    }
}

fun validateId(newId: StreamId, lastId: StreamId?): String? {
    // 0-0 검증
    if (newId == StreamId.MIN) {
        return "ERR The ID specified in XADD must be greater than 0-0"
    }
    
    // 순서 검증
    if (lastId != null && newId <= lastId) {
        return "ERR The ID specified in XADD is equal or smaller than the target stream top item"
    }
    
    return null  // 유효함
}
```

---

### 🧪 테스트 방법

```bash
redis-cli
127.0.0.1:6379> XADD mystream 1-1 field value
"1-1"

# 0-0은 사용 불가
127.0.0.1:6379> XADD mystream 0-0 field value
(error) ERR The ID specified in XADD must be greater than 0-0

# 기존 ID보다 작으면 에러
127.0.0.1:6379> XADD mystream 0-1 field value
(error) ERR The ID specified in XADD is equal or smaller than the target stream top item

# 같은 ID도 에러
127.0.0.1:6379> XADD mystream 1-1 field value
(error) ERR The ID specified in XADD is equal or smaller than the target stream top item

# 더 큰 ID는 성공
127.0.0.1:6379> XADD mystream 1-2 field value
"1-2"

127.0.0.1:6379> XADD mystream 2-0 field value
"2-0"
```

---

### 🤔 생각해볼 점

왜 Redis는 ID가 항상 증가하도록 강제할까요?

- **시간 순서 보장**: 스트림은 이벤트 로그로 사용되므로 순서가 중요합니다
- **효율적인 범위 쿼리**: ID 기반으로 특정 시간 범위의 데이터를 빠르게 조회할 수 있습니다
- **중복 방지**: 같은 ID의 엔트리가 여러 개 존재할 수 없습니다

---

### ➡️ 다음 단계

Stage 4에서는 **시퀀스 번호를 자동 생성**하는 기능을 구현합니다 (부분 자동 생성 ID).
