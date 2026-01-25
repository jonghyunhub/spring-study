# Redis Streams 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | TYPE 명령어 구현하기 | ⬤○○ 쉬움 |
| 2 | 스트림 생성하기 | ⬤⬤○ 보통 |
| 3 | 엔트리 ID 유효성 검증 | ⬤○○ 쉬움 |
| 4 | 부분 자동 생성 ID | ⬤⬤○ 보통 |
| 5 | 완전 자동 생성 ID | ⬤⬤○ 보통 |
| **6** | **스트림에서 엔트리 조회하기** | ⬤⬤○ 보통 |
| 7 | `-` 를 사용한 쿼리 | ⬤○○ 쉬움 |
| 8 | `+` 를 사용한 쿼리 | ⬤○○ 쉬움 |
| 9 | XREAD로 단일 스트림 조회 | ⬤⬤○ 보통 |
| 10 | XREAD로 다중 스트림 조회 | ⬤⬤○ 보통 |
| 11 | 블로킹 읽기 | ⬤⬤⬤ 어려움 |
| 12 | 타임아웃 없는 블로킹 읽기 | ⬤⬤○ 보통 |
| 13 | `$` 를 사용한 블로킹 읽기 | ⬤○○ 쉬움 |

---

## Stage 6: 스트림에서 엔트리 조회하기

### 🎯 목표

`XRANGE` 명령어를 구현하여 **스트림에서 특정 범위의 엔트리를 조회**합니다.

---

### 📚 배경 지식

#### XRANGE 명령어

```
XRANGE key start end
```

- `key`: 스트림 이름
- `start`: 시작 ID (이 ID 포함)
- `end`: 끝 ID (이 ID 포함)

지정한 범위 내의 모든 엔트리를 **오름차순**으로 반환합니다.

#### 응답 형식

RESP Array 형식으로 반환합니다:

```
*2                          # 2개의 엔트리
*2                          # 첫 번째 엔트리
$15                         # ID
1526919030474-0
*2                          # 필드-값 배열
$11
temperature
$2
36
*2                          # 두 번째 엔트리
$15
1526919030474-1
*4
$11
temperature
$2
37
$8
humidity
$2
94
```

---

### ✅ 통과 조건

- 명시적 ID 범위로 엔트리를 조회할 수 있어야 합니다
- start와 end ID가 결과에 포함되어야 합니다 (inclusive)
- 결과가 없으면 빈 배열을 반환해야 합니다

---

### 💡 힌트

RESP 배열 응답 포맷터를 구현하세요:

```kotlin
// 배열 응답 생성
fun respArray(items: List<String>): String {
    if (items.isEmpty()) return "*0\r\n"
    return "*${items.size}\r\n${items.joinToString("")}"
}

// Bulk String 생성
fun bulkString(value: String): String = "\$${value.length}\r\n$value\r\n"

// 엔트리를 RESP 형식으로 변환
fun entryToResp(entry: StreamEntry): String {
    val idPart = bulkString(entry.id)
    
    val fieldValues = entry.fields.flatMap { (k, v) -> listOf(k, v) }
    val fieldsPart = respArray(fieldValues.map { bulkString(it) })
    
    return respArray(listOf(idPart, fieldsPart))
}

fun handleXRange(args: List<String>): String {
    val key = args[0]
    val startId = StreamId.parse(args[1])
    val endId = StreamId.parse(args[2])
    
    val stream = getStream(key) ?: return "*0\r\n"
    
    val entries = stream.entries.filter { entry ->
        val id = StreamId.parse(entry.id)
        id >= startId && id <= endId
    }
    
    return respArray(entries.map { entryToResp(it) })
}
```

---

### 🧪 테스트 방법

```bash
redis-cli

# 테스트 데이터 추가
127.0.0.1:6379> XADD mystream 1-0 a 1
"1-0"
127.0.0.1:6379> XADD mystream 2-0 b 2
"2-0"
127.0.0.1:6379> XADD mystream 3-0 c 3
"3-0"
127.0.0.1:6379> XADD mystream 4-0 d 4
"4-0"

# 범위 조회
127.0.0.1:6379> XRANGE mystream 2-0 3-0
1) 1) "2-0"
   2) 1) "b"
      2) "2"
2) 1) "3-0"
   2) 1) "c"
      2) "3"

# 전체 조회 (1-0 ~ 4-0)
127.0.0.1:6379> XRANGE mystream 1-0 4-0
1) 1) "1-0"
   2) 1) "a"
      2) "1"
2) 1) "2-0"
   2) 1) "b"
      2) "2"
3) 1) "3-0"
   2) 1) "c"
      2) "3"
4) 1) "4-0"
   2) 1) "d"
      2) "4"

# 범위에 해당하는 엔트리가 없으면 빈 배열
127.0.0.1:6379> XRANGE mystream 10-0 20-0
(empty array)
```

---

### 🤔 생각해볼 점

매번 모든 엔트리를 순회하면서 필터링하는 것은 비효율적입니다. 실제 Redis는 어떻게 최적화할까요?

- **Radix Tree**: Redis는 내부적으로 Radix Tree 구조를 사용합니다
- **이진 탐색**: ID가 정렬되어 있으므로 범위의 시작점을 빠르게 찾을 수 있습니다

---

### ➡️ 다음 단계

Stage 7에서는 **`-` 특수 ID로 스트림의 처음부터 조회**하는 기능을 구현합니다.
