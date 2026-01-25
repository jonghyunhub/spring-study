# Redis Streams 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | TYPE 명령어 구현하기 | ⬤○○ 쉬움 |
| 2 | 스트림 생성하기 | ⬤⬤○ 보통 |
| 3 | 엔트리 ID 유효성 검증 | ⬤○○ 쉬움 |
| **4** | **부분 자동 생성 ID** | ⬤⬤○ 보통 |
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

## Stage 4: 부분 자동 생성 ID

### 🎯 목표

타임스탬프는 직접 지정하고 **시퀀스 번호만 자동 생성**되도록 `XADD` 명령어를 확장합니다.

---

### 📚 배경 지식

#### 부분 자동 생성 ID란?

ID에서 시퀀스 번호 부분을 `*`로 지정하면, Redis가 자동으로 적절한 시퀀스 번호를 생성합니다:

```
XADD mystream 1526919030474-* field value
                          ↑
                    자동 생성됨
```

#### 시퀀스 번호 생성 규칙

| 상황 | 생성되는 시퀀스 번호 |
|------|---------------------|
| 스트림이 비어있음 | `0` (단, 타임스탬프가 0이면 `1`) |
| 마지막 엔트리와 타임스탬프가 같음 | `마지막 시퀀스 + 1` |
| 마지막 엔트리보다 타임스탬프가 큼 | `0` |

**특수 케이스**: 타임스탬프가 `0`일 때 최소 시퀀스 번호는 `1`입니다 (`0-0`은 예약된 ID이므로).

---

### ✅ 통과 조건

- `1526919030474-*` 형식의 ID를 처리할 수 있어야 합니다
- 시퀀스 번호가 올바르게 자동 생성되어야 합니다
- 생성된 전체 ID가 응답으로 반환되어야 합니다

---

### 💡 힌트

```kotlin
fun generateSequence(timestamp: Long, lastId: StreamId?): Long {
    // 스트림이 비어있는 경우
    if (lastId == null) {
        return if (timestamp == 0L) 1 else 0
    }
    
    // 같은 타임스탬프인 경우: 시퀀스 증가
    if (timestamp == lastId.milliseconds) {
        return lastId.sequence + 1
    }
    
    // 더 큰 타임스탬프인 경우: 0부터 시작
    // (더 작은 타임스탬프는 유효성 검증에서 거부됨)
    return 0
}

fun handleXAdd(args: List<String>): String {
    val key = args[0]
    val idArg = args[1]
    
    val id = if (idArg.endsWith("-*")) {
        // 부분 자동 생성
        val timestamp = idArg.removeSuffix("-*").toLong()
        val lastId = getLastEntryId(key)
        val sequence = generateSequence(timestamp, lastId)
        StreamId(timestamp, sequence)
    } else {
        // 명시적 ID
        StreamId.parse(idArg)
    }
    
    // 유효성 검증 후 추가...
}
```

---

### 🧪 테스트 방법

```bash
redis-cli

# 빈 스트림에 추가 - 시퀀스 0
127.0.0.1:6379> XADD mystream 1526919030474-* field1 value1
"1526919030474-0"

# 같은 타임스탬프 - 시퀀스 1
127.0.0.1:6379> XADD mystream 1526919030474-* field2 value2
"1526919030474-1"

# 같은 타임스탬프 - 시퀀스 2
127.0.0.1:6379> XADD mystream 1526919030474-* field3 value3
"1526919030474-2"

# 새로운 타임스탬프 - 시퀀스 0
127.0.0.1:6379> XADD mystream 1526919030475-* field4 value4
"1526919030475-0"

# 타임스탬프 0일 때 - 시퀀스는 최소 1
127.0.0.1:6379> XADD newstream 0-* field value
"0-1"
```

---

### 🤔 생각해볼 점

왜 타임스탬프 `0`일 때 시퀀스 번호가 `1`부터 시작할까요?

`0-0`은 Redis에서 **"스트림의 시작"**을 나타내는 특별한 ID입니다. `XREAD`나 `XRANGE` 명령어에서 "처음부터"를 의미할 때 사용됩니다. 따라서 실제 데이터에는 `0-0`을 사용할 수 없습니다.

---

### ➡️ 다음 단계

Stage 5에서는 **타임스탬프와 시퀀스 번호 모두 자동 생성**하는 기능을 구현합니다 (완전 자동 생성 ID).
