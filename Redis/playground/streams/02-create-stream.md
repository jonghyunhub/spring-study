# Redis Streams 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | TYPE 명령어 구현하기 | ⬤○○ 쉬움 |
| **2** | **스트림 생성하기** | ⬤⬤○ 보통 |
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

## Stage 2: 스트림 생성하기

### 🎯 목표

`XADD` 명령어를 구현하여 **스트림에 새로운 엔트리를 추가**합니다. 스트림이 존재하지 않으면 자동으로 생성됩니다.

---

### 📚 배경 지식

#### Redis Streams 구조

Redis Streams는 **추가 전용(append-only) 로그** 데이터 구조입니다:

```
Stream: mystream
┌─────────────────┬─────────────────┬─────────────────┐
│ 1526919030474-0 │ 1526919030474-1 │ 1526919030475-0 │
│ temp: 36        │ temp: 37        │ temp: 38        │
│ humidity: 95    │ humidity: 94    │ humidity: 93    │
└─────────────────┴─────────────────┴─────────────────┘
      Entry 1           Entry 2           Entry 3
```

#### 엔트리 ID 구조

각 엔트리는 고유한 ID를 가집니다:

```
<millisecondsTime>-<sequenceNumber>
```

- `millisecondsTime`: Unix 타임스탬프 (밀리초 단위)
- `sequenceNumber`: 같은 밀리초 내에서의 순서 번호

예시: `1526919030474-0`, `1526919030474-1`

#### XADD 명령어 형식

```
XADD key id field value [field value ...]
```

- `key`: 스트림 이름
- `id`: 엔트리 ID (명시적 지정)
- `field value`: 하나 이상의 필드-값 쌍

---

### ✅ 통과 조건

- `XADD`로 스트림에 엔트리를 추가할 수 있어야 합니다
- 스트림이 없으면 자동으로 생성되어야 합니다
- 추가된 엔트리의 ID가 응답으로 반환되어야 합니다
- `TYPE` 명령어로 확인 시 `stream`을 반환해야 합니다

---

### 💡 힌트

데이터 구조를 먼저 설계하세요:

```kotlin
data class StreamEntry(
    val id: String,
    val fields: Map<String, String>
)

data class RedisStream(
    val entries: MutableList<StreamEntry> = mutableListOf()
)

// 응답은 Bulk String 형식
fun bulkString(value: String): String = "\$${value.length}\r\n$value\r\n"
```

XADD 명령어 파싱 예시:

```kotlin
// 입력: XADD mystream 1526919030474-0 temperature 36 humidity 95
fun handleXAdd(args: List<String>): String {
    val key = args[0]           // mystream
    val id = args[1]            // 1526919030474-0
    val fields = mutableMapOf<String, String>()
    
    // 필드-값 쌍 파싱 (인덱스 2부터 끝까지)
    for (i in 2 until args.size step 2) {
        fields[args[i]] = args[i + 1]
    }
    
    // 스트림에 엔트리 추가
    val stream = getOrCreateStream(key)
    stream.entries.add(StreamEntry(id, fields))
    
    return bulkString(id)
}
```

---

### 🧪 테스트 방법

```bash
# redis-cli로 테스트
redis-cli
127.0.0.1:6379> XADD mystream 1526919030474-0 temperature 36 humidity 95
"1526919030474-0"

127.0.0.1:6379> XADD mystream 1526919030474-1 temperature 37 humidity 94
"1526919030474-1"

127.0.0.1:6379> TYPE mystream
stream
```

---

### 🤔 생각해볼 점

현재 구현에서는 **어떤 ID든 허용**합니다. 하지만 실제 Redis는 엔트리 ID가 항상 증가해야 한다는 규칙이 있습니다. 다음 단계에서 이 유효성 검증을 추가합니다.

---

### ➡️ 다음 단계

Stage 3에서는 **엔트리 ID의 유효성을 검증**하는 기능을 구현합니다.
