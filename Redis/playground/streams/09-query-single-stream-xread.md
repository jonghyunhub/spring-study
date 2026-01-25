# Redis Streams 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | TYPE 명령어 구현하기 | ⬤○○ 쉬움 |
| 2 | 스트림 생성하기 | ⬤⬤○ 보통 |
| 3 | 엔트리 ID 유효성 검증 | ⬤○○ 쉬움 |
| 4 | 부분 자동 생성 ID | ⬤⬤○ 보통 |
| 5 | 완전 자동 생성 ID | ⬤⬤○ 보통 |
| 6 | 스트림에서 엔트리 조회하기 | ⬤⬤○ 보통 |
| 7 | `-` 를 사용한 쿼리 | ⬤○○ 쉬움 |
| 8 | `+` 를 사용한 쿼리 | ⬤○○ 쉬움 |
| **9** | **XREAD로 단일 스트림 조회** | ⬤⬤○ 보통 |
| 10 | XREAD로 다중 스트림 조회 | ⬤⬤○ 보통 |
| 11 | 블로킹 읽기 | ⬤⬤⬤ 어려움 |
| 12 | 타임아웃 없는 블로킹 읽기 | ⬤⬤○ 보통 |
| 13 | `$` 를 사용한 블로킹 읽기 | ⬤○○ 쉬움 |

---

## Stage 9: XREAD로 단일 스트림 조회

### 🎯 목표

`XREAD` 명령어를 구현하여 **특정 ID 이후의 엔트리를 조회**합니다.

---

### 📚 배경 지식

#### XREAD vs XRANGE

| 특성 | XRANGE | XREAD |
|------|--------|-------|
| ID 범위 | start ~ end (inclusive) | start 초과 (exclusive) |
| 여러 스트림 | ❌ | ✅ |
| 블로킹 지원 | ❌ | ✅ |
| 주요 용도 | 과거 데이터 조회 | 새 데이터 폴링/구독 |

#### XREAD 명령어 형식

```
XREAD STREAMS key [key ...] id [id ...]
```

**중요**: ID는 **exclusive**입니다. 지정한 ID **이후의** 엔트리만 반환합니다.

```
XREAD STREAMS mystream 1-0
                       ↑
                 이 ID는 포함하지 않음
                 1-0 이후(>) 엔트리만 반환
```

#### 응답 형식

스트림 이름과 엔트리 목록을 함께 반환합니다:

```
*1                    # 1개의 스트림
*2                    # [스트림이름, 엔트리들]
$8
mystream
*2                    # 2개의 엔트리
*2                    # 첫 번째 엔트리
$3
2-0
*2
$1
b
$1
2
...
```

---

### ✅ 통과 조건

- `XREAD STREAMS mystream <id>` 형식의 쿼리를 처리할 수 있어야 합니다
- 지정한 ID **이후의** 엔트리만 반환해야 합니다 (exclusive)
- 결과가 없으면 null 응답(`$-1\r\n`)을 반환해야 합니다

---

### 💡 힌트

```kotlin
fun handleXRead(args: List<String>): String {
    // XREAD STREAMS mystream 0-0
    // args = ["STREAMS", "mystream", "0-0"]
    
    val streamsIndex = args.indexOfFirst { it.uppercase() == "STREAMS" }
    if (streamsIndex == -1) return "-ERR syntax error\r\n"
    
    // STREAMS 이후의 인자들
    val streamArgs = args.subList(streamsIndex + 1, args.size)
    
    // 단일 스트림의 경우: [key, id]
    val key = streamArgs[0]
    val afterId = StreamId.parse(streamArgs[1])
    
    val stream = getStream(key) ?: return "\$-1\r\n"  // null bulk string
    
    // afterId보다 큰 엔트리만 필터링 (exclusive)
    val entries = stream.entries.filter { entry ->
        StreamId.parse(entry.id) > afterId
    }
    
    if (entries.isEmpty()) return "\$-1\r\n"
    
    // 응답 생성: [[streamName, entries]]
    return buildXReadResponse(key, entries)
}

fun buildXReadResponse(key: String, entries: List<StreamEntry>): String {
    val entriesResp = respArray(entries.map { entryToResp(it) })
    val streamResp = respArray(listOf(bulkString(key), entriesResp))
    return respArray(listOf(streamResp))
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

# 0-0 이후의 모든 엔트리 (전체)
127.0.0.1:6379> XREAD STREAMS mystream 0-0
1) 1) "mystream"
   2) 1) 1) "1-0"
         2) 1) "a"
            2) "1"
      2) 1) "2-0"
         2) 1) "b"
            2) "2"
      3) 1) "3-0"
         2) 1) "c"
            2) "3"

# 1-0 이후의 엔트리 (1-0은 포함 안 됨!)
127.0.0.1:6379> XREAD STREAMS mystream 1-0
1) 1) "mystream"
   2) 1) 1) "2-0"
         2) 1) "b"
            2) "2"
      2) 1) "3-0"
         2) 1) "c"
            2) "3"

# 마지막 ID 이후 (결과 없음)
127.0.0.1:6379> XREAD STREAMS mystream 3-0
(nil)
```

---

### 🤔 생각해볼 점

#### 왜 XREAD는 exclusive인가요?

이벤트 폴링 시나리오를 생각해보세요:

```kotlin
var lastId = "0-0"

while (true) {
    val result = redis.xread("STREAMS", "events", lastId)
    
    if (result != null) {
        for (entry in result) {
            process(entry)
            lastId = entry.id  // 마지막 처리한 ID 저장
        }
    }
    
    sleep(1000)  // 잠시 대기 후 다시 폴링
}
```

만약 inclusive라면 마지막 ID를 직접 계산해야 하는 번거로움이 있습니다.

---

### ➡️ 다음 단계

Stage 10에서는 **XREAD로 여러 스트림을 동시에 조회**하는 기능을 구현합니다.
