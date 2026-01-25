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
| 10 | XREAD로 다중 스트림 조회 | ⬤⬤○ 보통 |
| **11** | **블로킹 읽기** | ⬤⬤⬤ 어려움 |
| 12 | 타임아웃 없는 블로킹 읽기 | ⬤⬤○ 보통 |
| 13 | `$` 를 사용한 블로킹 읽기 | ⬤○○ 쉬움 |

---

## Stage 11: 블로킹 읽기

### 🎯 목표

`XREAD` 명령어에 **BLOCK 옵션**을 추가하여 새로운 데이터가 도착할 때까지 대기할 수 있도록 구현합니다.

---

### 📚 배경 지식

#### 블로킹 읽기란?

일반 `XREAD`는 데이터가 없으면 즉시 null을 반환합니다. 블로킹 `XREAD`는 **새 데이터가 도착하거나 타임아웃될 때까지 대기**합니다.

```
XREAD BLOCK 5000 STREAMS mystream 0-0
            ↑
      최대 5000ms(5초) 대기
```

#### 폴링 vs 블로킹

| 방식 | 장점 | 단점 |
|------|------|------|
| 폴링 | 구현 간단 | CPU 낭비, 지연 발생 |
| 블로킹 | 효율적, 즉각 반응 | 구현 복잡 |

```kotlin
// 폴링 방식
while (true) {
    val data = redis.xread("STREAMS", "events", lastId)
    if (data != null) process(data)
    Thread.sleep(100)  // CPU 낭비 + 최대 100ms 지연
}

// 블로킹 방식
while (true) {
    val data = redis.xread("BLOCK", "0", "STREAMS", "events", lastId)
    // 새 데이터가 오면 즉시 반환, 대기 중 CPU 사용 없음
    process(data)
}
```

#### 명령어 형식

```
XREAD [BLOCK milliseconds] STREAMS key [key ...] id [id ...]
```

- `BLOCK 0`: 무한 대기
- `BLOCK 5000`: 최대 5초 대기
- `BLOCK` 생략: 블로킹 없이 즉시 반환

---

### ✅ 통과 조건

- `XREAD BLOCK <ms> STREAMS ...` 형식을 파싱할 수 있어야 합니다
- 데이터가 없을 때 지정된 시간만큼 대기해야 합니다
- 대기 중 새 데이터가 추가되면 즉시 반환해야 합니다
- 타임아웃 시 null을 반환해야 합니다

---

### 💡 힌트

이 단계는 **동시성 처리**가 필요하여 복잡합니다.

#### 핵심 아이디어

1. 대기 중인 클라이언트를 별도 목록으로 관리
2. `XADD` 시 대기 중인 클라이언트에게 알림
3. 타임아웃 스레드로 대기 시간 관리

```kotlin
import java.util.concurrent.*

// 대기 중인 클라이언트 정보
data class WaitingClient(
    val keys: List<String>,
    val afterIds: Map<String, StreamId>,
    val response: CompletableFuture<String>,
    val expireAt: Long?  // null이면 무한 대기
)

val waitingClients = ConcurrentLinkedQueue<WaitingClient>()

fun handleXRead(args: List<String>): String? {
    var blockMs: Long? = null
    var argsStartIndex = 0
    
    // BLOCK 옵션 파싱
    if (args[0].uppercase() == "BLOCK") {
        blockMs = args[1].toLong()
        argsStartIndex = 2
    }
    
    // STREAMS 파싱 (기존 로직)
    val (keys, afterIds) = parseStreamsArgs(args.subList(argsStartIndex, args.size))
    
    // 즉시 조회 시도
    val result = queryStreams(keys, afterIds)
    if (result != null) {
        return result
    }
    
    // 블로킹 요청이 아니면 null 반환
    if (blockMs == null) {
        return "\$-1\r\n"
    }
    
    // 블로킹: 대기 큐에 등록
    val future = CompletableFuture<String>()
    val expireAt = if (blockMs == 0L) null else System.currentTimeMillis() + blockMs
    
    val waiting = WaitingClient(keys, afterIds.toMap(), future, expireAt)
    waitingClients.add(waiting)
    
    // 타임아웃 처리
    if (blockMs > 0) {
        scheduler.schedule({
            if (!future.isDone) {
                waitingClients.remove(waiting)
                future.complete("\$-1\r\n")
            }
        }, blockMs, TimeUnit.MILLISECONDS)
    }
    
    // 비동기로 응답 대기
    return future.get()  // 또는 비동기로 처리
}

// XADD에서 대기 클라이언트 깨우기
fun handleXAdd(args: List<String>): String {
    // 기존 XADD 로직...
    val key = args[0]
    val entryId = addEntry(key, /* ... */)
    
    // 대기 중인 클라이언트 확인
    val iterator = waitingClients.iterator()
    while (iterator.hasNext()) {
        val waiting = iterator.next()
        if (key in waiting.keys) {
            val result = queryStreams(waiting.keys, waiting.afterIds)
            if (result != null) {
                iterator.remove()
                waiting.response.complete(result)
            }
        }
    }
    
    return bulkString(entryId)
}
```

---

### 🧪 테스트 방법

터미널 두 개를 사용합니다:

```bash
# 터미널 1: 블로킹 읽기 (5초 대기)
127.0.0.1:6379> XREAD BLOCK 5000 STREAMS mystream 0-0
# ... 대기 중 ...

# 터미널 2: 데이터 추가 (터미널 1이 대기 중일 때)
127.0.0.1:6379> XADD mystream * message "hello"
"1609459200000-0"

# 터미널 1: 즉시 결과 반환
1) 1) "mystream"
   2) 1) 1) "1609459200000-0"
         2) 1) "message"
            2) "hello"
```

타임아웃 테스트:

```bash
# 데이터 없이 대기 -> 3초 후 타임아웃
127.0.0.1:6379> XREAD BLOCK 3000 STREAMS emptystream 0-0
(nil)
# 3초 후 반환
```

---

### 🤔 생각해볼 점

#### 동시성 모델 선택

블로킹 구현에는 여러 방법이 있습니다:

1. **스레드 풀 + 조건 변수**: Java 전통 방식
2. **CompletableFuture**: 비동기 프로그래밍
3. **Coroutines** (Kotlin): 가장 우아한 방식
4. **이벤트 루프** (Netty 등): 고성능 서버

#### 공정성 (Fairness)

여러 클라이언트가 같은 스트림을 대기하면 누가 먼저 응답받을까요? FIFO? 랜덤? 이런 정책도 고려해야 합니다.

---

### ➡️ 다음 단계

Stage 12에서는 **타임아웃 0으로 무한 대기**하는 기능을 구현합니다.
