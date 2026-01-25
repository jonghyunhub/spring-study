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
| 9 | XREAD로 단일 스트림 조회 | ⬤⬤○ 보통 |
| **10** | **XREAD로 다중 스트림 조회** | ⬤⬤○ 보통 |
| 11 | 블로킹 읽기 | ⬤⬤⬤ 어려움 |
| 12 | 타임아웃 없는 블로킹 읽기 | ⬤⬤○ 보통 |
| 13 | `$` 를 사용한 블로킹 읽기 | ⬤○○ 쉬움 |

---

## Stage 10: XREAD로 다중 스트림 조회

### 🎯 목표

`XREAD` 명령어가 **여러 스트림을 동시에 조회**할 수 있도록 확장합니다.

---

### 📚 배경 지식

#### 다중 스트림 조회

`XREAD`는 한 번의 호출로 여러 스트림에서 데이터를 가져올 수 있습니다:

```
XREAD STREAMS stream1 stream2 stream3 id1 id2 id3
               ─────────────────────   ─────────
                    스트림 키들          각 스트림의 시작 ID
```

**중요**: 스트림 키와 ID는 **순서가 대응**됩니다:
- `stream1` → `id1`
- `stream2` → `id2`
- `stream3` → `id3`

#### 명령어 구조

```
XREAD STREAMS key1 key2 key3 0-0 0-0 1-0
              ├──────────────┤ ├─────────┤
                 N개의 키       N개의 ID
```

키 개수와 ID 개수는 반드시 같아야 합니다.

#### 응답 구조

새 엔트리가 있는 스트림만 응답에 포함됩니다:

```
*2                    # 2개의 스트림에서 결과
*2                    # 첫 번째 스트림
$7
stream1
*1                    # 1개의 엔트리
...
*2                    # 두 번째 스트림
$7
stream2
*2                    # 2개의 엔트리
...
```

---

### ✅ 통과 조건

- 여러 스트림을 동시에 조회할 수 있어야 합니다
- 각 스트림에 대해 올바른 시작 ID가 적용되어야 합니다
- 새 엔트리가 있는 스트림만 응답에 포함되어야 합니다
- 모든 스트림에 새 엔트리가 없으면 null을 반환해야 합니다

---

### 💡 힌트

키와 ID를 페어링하는 로직:

```kotlin
fun handleXRead(args: List<String>): String {
    val streamsIndex = args.indexOfFirst { it.uppercase() == "STREAMS" }
    val streamArgs = args.subList(streamsIndex + 1, args.size)
    
    // 키와 ID의 개수는 같아야 함
    val count = streamArgs.size / 2
    val keys = streamArgs.subList(0, count)
    val ids = streamArgs.subList(count, streamArgs.size)
    
    // 각 스트림에서 엔트리 조회
    val results = mutableListOf<Pair<String, List<StreamEntry>>>()
    
    for (i in 0 until count) {
        val key = keys[i]
        val afterId = StreamId.parse(ids[i])
        
        val stream = getStream(key) ?: continue
        
        val entries = stream.entries.filter { entry ->
            StreamId.parse(entry.id) > afterId
        }
        
        if (entries.isNotEmpty()) {
            results.add(key to entries)
        }
    }
    
    if (results.isEmpty()) return "\$-1\r\n"
    
    return buildMultiStreamResponse(results)
}

fun buildMultiStreamResponse(
    results: List<Pair<String, List<StreamEntry>>>
): String {
    val streamResponses = results.map { (key, entries) ->
        val entriesResp = respArray(entries.map { entryToResp(it) })
        respArray(listOf(bulkString(key), entriesResp))
    }
    return respArray(streamResponses)
}
```

---

### 🧪 테스트 방법

```bash
redis-cli

# 두 개의 스트림에 데이터 추가
127.0.0.1:6379> XADD stream1 1-0 a 1
"1-0"
127.0.0.1:6379> XADD stream1 2-0 b 2
"2-0"
127.0.0.1:6379> XADD stream2 1-0 x 10
"1-0"
127.0.0.1:6379> XADD stream2 2-0 y 20
"2-0"

# 두 스트림 동시 조회
127.0.0.1:6379> XREAD STREAMS stream1 stream2 0-0 0-0
1) 1) "stream1"
   2) 1) 1) "1-0"
         2) 1) "a"
            2) "1"
      2) 1) "2-0"
         2) 1) "b"
            2) "2"
2) 1) "stream2"
   2) 1) 1) "1-0"
         2) 1) "x"
            2) "10"
      2) 1) "2-0"
         2) 1) "y"
            2) "20"

# 다른 시작 ID로 조회
127.0.0.1:6379> XREAD STREAMS stream1 stream2 1-0 0-0
1) 1) "stream1"
   2) 1) 1) "2-0"          # stream1은 1-0 이후만
         2) 1) "b"
            2) "2"
2) 1) "stream2"
   2) 1) 1) "1-0"          # stream2는 0-0 이후 전체
         2) 1) "x"
            2) "10"
      2) 1) "2-0"
         2) 1) "y"
            2) "20"

# 한 스트림에만 새 엔트리가 있는 경우
127.0.0.1:6379> XREAD STREAMS stream1 stream2 2-0 0-0
1) 1) "stream2"            # stream1은 결과 없음, stream2만 반환
   2) 1) 1) "1-0"
         2) 1) "x"
            2) "10"
      2) 1) "2-0"
         2) 1) "y"
            2) "20"
```

---

### 🤔 생각해볼 점

#### 실제 사용 시나리오

여러 토픽의 이벤트를 하나의 소비자가 처리하는 경우:

```kotlin
val lastIds = mutableMapOf(
    "orders" to "0-0",
    "payments" to "0-0",
    "notifications" to "0-0"
)

while (true) {
    val result = redis.xread(
        "STREAMS", 
        "orders", "payments", "notifications",
        lastIds["orders"], lastIds["payments"], lastIds["notifications"]
    )
    
    // 각 스트림의 이벤트 처리 및 lastIds 업데이트
}
```

---

### ➡️ 다음 단계

Stage 11에서는 **BLOCK 옵션으로 새 데이터를 대기**하는 기능을 구현합니다.
