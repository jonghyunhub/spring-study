# Redis Streams 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| **1** | **TYPE 명령어 구현하기** | ⬤○○ 쉬움 |
| 2 | 스트림 생성하기 | ⬤⬤○ 보통 |
| 3 | 엔트리 ID 유효성 검증 | ⬤○○ 쉬움 |
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

## Stage 1: TYPE 명령어 구현하기

### 🎯 목표

Redis의 `TYPE` 명령어를 구현합니다. 이 명령어는 주어진 키에 저장된 **데이터 타입을 문자열로 반환**합니다.

---

### 📚 배경 지식

#### Redis 데이터 타입

Redis는 다양한 데이터 타입을 지원하며, 각 타입마다 전용 명령어 세트가 있습니다:

| 타입 | 설명 | 주요 명령어 |
|------|------|-------------|
| `string` | 문자열 | SET, GET |
| `list` | 리스트 | LPUSH, RPUSH, LPOP |
| `set` | 집합 | SADD, SMEMBERS |
| `zset` | 정렬된 집합 | ZADD, ZRANGE |
| `hash` | 해시 | HSET, HGET |
| `stream` | 스트림 | XADD, XREAD |

#### TYPE 명령어

```
TYPE key
```

- 키가 존재하면 해당 데이터 타입을 반환
- 키가 존재하지 않으면 `none`을 반환

---

### ✅ 통과 조건

- 존재하지 않는 키에 대해 `none`을 반환해야 합니다
- 스트림 타입의 키에 대해 `stream`을 반환해야 합니다
- 문자열 타입의 키에 대해 `string`을 반환해야 합니다

---

### 💡 힌트

응답은 **Simple String** 형식으로 반환합니다:

```
+타입명\r\n
```

내부적으로 키-값 저장소에서 값의 타입을 확인하는 로직이 필요합니다.

```kotlin
// 데이터 저장소 구조 예시
sealed class RedisValue {
    data class StringValue(val value: String) : RedisValue()
    data class StreamValue(val entries: MutableList<StreamEntry>) : RedisValue()
    // ... 다른 타입들
}

val store = mutableMapOf<String, RedisValue>()

fun getType(key: String): String {
    return when (store[key]) {
        is RedisValue.StringValue -> "string"
        is RedisValue.StreamValue -> "stream"
        null -> "none"
        // ... 다른 타입들
    }
}
```

---

### 🧪 테스트 방법

```bash
# redis-cli로 테스트
redis-cli
127.0.0.1:6379> TYPE nonexistent
none

127.0.0.1:6379> SET mykey "hello"
OK
127.0.0.1:6379> TYPE mykey
string

127.0.0.1:6379> XADD mystream * field value
"1234567890123-0"
127.0.0.1:6379> TYPE mystream
stream
```

---

### 🤔 생각해볼 점

`TYPE` 명령어는 단순해 보이지만, Redis의 **다형성(polymorphism)** 설계를 이해하는 데 중요합니다. 같은 키 공간에서 다양한 타입의 데이터를 관리하면서도 타입 안전성을 유지해야 합니다.

---

### ➡️ 다음 단계

Stage 2에서는 **XADD 명령어로 스트림을 생성**하는 기능을 구현합니다.
