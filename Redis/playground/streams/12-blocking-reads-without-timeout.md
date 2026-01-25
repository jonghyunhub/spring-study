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
| 11 | 블로킹 읽기 | ⬤⬤⬤ 어려움 |
| **12** | **타임아웃 없는 블로킹 읽기** | ⬤⬤○ 보통 |
| 13 | `$` 를 사용한 블로킹 읽기 | ⬤○○ 쉬움 |

---

## Stage 12: 타임아웃 없는 블로킹 읽기

### 🎯 목표

`BLOCK 0` 옵션을 사용하여 **새 데이터가 도착할 때까지 무한정 대기**하는 기능을 구현합니다.

---

### 📚 배경 지식

#### BLOCK 0의 의미

`BLOCK 0`은 **타임아웃 없이 무한 대기**를 의미합니다:

```
XREAD BLOCK 0 STREAMS mystream 0-0
            ↑
      무한 대기 (데이터 올 때까지)
```

일반적인 메시지 소비자 패턴에서 많이 사용됩니다.

#### 일반 타임아웃 vs BLOCK 0

| BLOCK 값 | 동작 |
|----------|------|
| 생략 | 블로킹 없음, 즉시 반환 |
| `BLOCK 5000` | 최대 5초 대기, 타임아웃 시 null |
| `BLOCK 0` | 무한 대기, 데이터가 올 때까지 |

---

### ✅ 통과 조건

- `BLOCK 0`으로 무한 대기할 수 있어야 합니다
- 타임아웃 없이 데이터가 도착할 때까지 계속 대기해야 합니다
- 데이터가 추가되면 즉시 반환해야 합니다

---

### 💡 힌트

Stage 11의 구현에서 `blockMs == 0`인 경우를 특별히 처리합니다:

```kotlin
fun handleXReadBlock(args: List<String>): String {
    val blockMs = args[1].toLong()
    
    // ... 기존 로직 ...
    
    val future = CompletableFuture<String>()
    val waiting = WaitingClient(keys, afterIds.toMap(), future, expireAt = null)
    waitingClients.add(waiting)
    
    if (blockMs > 0) {
        // 유한 타임아웃: 스케줄러로 타임아웃 처리
        scheduler.schedule({
            if (!future.isDone) {
                waitingClients.remove(waiting)
                future.complete("\$-1\r\n")
            }
        }, blockMs, TimeUnit.MILLISECONDS)
    }
    // blockMs == 0인 경우: 타임아웃 스케줄 없음 -> 무한 대기
    
    return future.get()  // 데이터 올 때까지 대기
}
```

**핵심**: `blockMs == 0`일 때는 타임아웃 스케줄러를 등록하지 않습니다. 클라이언트는 `XADD`에 의해 깨워질 때까지 영원히 대기합니다.

---

### 🧪 테스트 방법

```bash
# 터미널 1: 무한 대기
127.0.0.1:6379> XREAD BLOCK 0 STREAMS mystream 0-0
# ... 무한 대기 중 (타임아웃 없음) ...

# 터미널 2: 몇 분 후에 데이터 추가
127.0.0.1:6379> XADD mystream * event "finally"
"1609459500000-0"

# 터미널 1: 즉시 반환!
1) 1) "mystream"
   2) 1) 1) "1609459500000-0"
         2) 1) "event"
            2) "finally"
```

---

### 🤔 생각해볼 점

#### 무한 대기의 위험성

- **연결 끊김 감지**: 클라이언트가 갑자기 종료되면?
- **리소스 관리**: 대기 중인 클라이언트가 계속 쌓이면?
- **Graceful Shutdown**: 서버 종료 시 대기 클라이언트 처리?

```kotlin
// 연결 상태 체크 예시
fun monitorConnection(client: WaitingClient, socket: Socket) {
    while (!client.response.isDone) {
        if (socket.isClosed) {
            waitingClients.remove(client)
            client.response.cancel(true)
            break
        }
        Thread.sleep(1000)
    }
}
```

#### 실제 사용 패턴

```kotlin
// 이벤트 소비자의 전형적인 패턴
while (isRunning) {
    try {
        val events = redis.xread("BLOCK", "0", "STREAMS", "events", lastId)
        for (event in events) {
            process(event)
            lastId = event.id
        }
    } catch (e: Exception) {
        log.error("Error processing events", e)
        Thread.sleep(1000)  // 잠시 대기 후 재시도
    }
}
```

---

### ➡️ 다음 단계

Stage 13에서는 **`$` 특수 ID로 새 엔트리만 대기**하는 기능을 구현합니다.
