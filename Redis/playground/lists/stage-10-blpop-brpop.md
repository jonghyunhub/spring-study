# Redis Lists 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 리스트 생성하기 | ⬤○○ 쉬움 |
| 2 | 요소 추가하기 (RPUSH) | ⬤○○ 쉬움 |
| 3 | 여러 요소 추가하기 | ⬤○○ 쉬움 |
| 4 | 요소 조회하기 - 양수 인덱스 (LRANGE) | ⬤○○ 쉬움 |
| 5 | 요소 조회하기 - 음수 인덱스 | ⬤○○ 쉬움 |
| 6 | 앞에 요소 추가하기 (LPUSH) | ⬤○○ 쉬움 |
| 7 | 리스트 길이 조회하기 (LLEN) | ⬤○○ 쉬움 |
| 8 | 요소 제거하기 (LPOP/RPOP) | ⬤○○ 쉬움 |
| 9 | 여러 요소 제거하기 | ⬤○○ 쉬움 |
| **10** | **블로킹 조회 (BLPOP/BRPOP)** | ⬤⬤○ 보통 |
| 11 | 타임아웃이 있는 블로킹 조회 | ⬤⬤○ 보통 |

---

## Stage 10: 블로킹 조회 (BLPOP/BRPOP)

### 🎯 목표

`BLPOP`과 `BRPOP` 명령어를 구현합니다. 이 명령어들은 리스트가 비어있으면 **요소가 추가될 때까지 대기(블로킹)** 합니다. 메시지 큐의 핵심 기능입니다!

---

### 📚 배경 지식

#### BLPOP / BRPOP 명령어

```
BLPOP <key> [key ...] <timeout>
BRPOP <key> [key ...] <timeout>
```

- 리스트에 요소가 있으면 즉시 제거하고 반환
- 리스트가 비어있으면 요소가 추가될 때까지 **대기**
- `timeout`: 최대 대기 시간 (초). `0`이면 무한 대기

#### 동작 흐름

```
클라이언트 A                    클라이언트 B
    |                               |
    | BLPOP myqueue 0               |
    | (대기 중...)                   |
    |                               | RPUSH myqueue "task1"
    | <-- ["myqueue", "task1"]      |
    |                               |
```

#### 여러 키 모니터링

여러 키를 동시에 모니터링할 수 있습니다:

```bash
BLPOP queue1 queue2 queue3 0
# queue1, queue2, queue3 중 하나라도 요소가 있으면 반환
# 우선순위: 왼쪽 키가 높음 (queue1 > queue2 > queue3)
```

#### 응답 형식

```
*2\r\n
$6\r\nmylist\r\n    # 어느 키에서 가져왔는지
$5\r\nhello\r\n     # 실제 값
```

타임아웃 시: `*-1\r\n` (Null Array)

#### 실무 활용: 작업 큐

```bash
# 프로듀서 (작업 생성)
RPUSH job_queue "process_order:123"
RPUSH job_queue "send_email:456"

# 컨슈머 (작업 처리) - 여러 워커가 동시에 실행 가능
while true:
    job = BLPOP job_queue 0   # 작업이 올 때까지 대기
    process(job)
```

---

### ✅ 통과 조건

- `BLPOP <key> <timeout>` 명령어가 동작해야 합니다
- 리스트에 요소가 있으면 즉시 반환해야 합니다
- 리스트가 비어있으면 요소가 추가될 때까지 대기해야 합니다
- 응답은 `[키, 값]` 형식의 배열이어야 합니다
- 다른 클라이언트가 PUSH하면 대기 중인 클라이언트가 깨어나야 합니다

---

### 💡 힌트

블로킹을 구현하려면 **대기 중인 클라이언트를 추적**해야 합니다.

```kotlin
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.Condition

// 키별 대기 조건
val lock = ReentrantLock()
val keyConditions = ConcurrentHashMap<String, Condition>()

data class WaitingClient(
    val keys: List<String>,
    val isLeft: Boolean,  // BLPOP이면 true, BRPOP이면 false
    val condition: Condition
)

val waitingClients = ConcurrentHashMap<String, MutableList<WaitingClient>>()

"BLPOP" -> {
    if (command.args.size < 2) {
        writer.write("-ERR wrong number of arguments for 'blpop' command\r\n")
        writer.flush()
        return
    }
    
    val timeout = command.args.last().toLongOrNull() ?: 0L
    val keys = command.args.dropLast(1)
    
    lock.lock()
    try {
        // 1. 먼저 요소가 있는 키가 있는지 확인 (우선순위: 왼쪽부터)
        for (key in keys) {
            val list = listStore[key]
            if (list != null && list.isNotEmpty()) {
                val element = list.removeAt(0)
                if (list.isEmpty()) listStore.remove(key)
                
                // [키, 값] 형식으로 응답
                writer.write("*2\r\n")
                writer.write("\$${key.length}\r\n$key\r\n")
                writer.write("\$${element.length}\r\n$element\r\n")
                writer.flush()
                return
            }
        }
        
        // 2. 모든 키가 비어있으면 대기 (timeout=0이면 무한 대기)
        val condition = lock.newCondition()
        val waitingClient = WaitingClient(keys, isLeft = true, condition)
        
        // 모든 관심 키에 대기 등록
        for (key in keys) {
            waitingClients.computeIfAbsent(key) { mutableListOf() }
                .add(waitingClient)
        }
        
        // 대기
        if (timeout == 0L) {
            condition.await()  // 무한 대기
        } else {
            val timedOut = !condition.await(timeout, TimeUnit.SECONDS)
            if (timedOut) {
                // 타임아웃: 대기 목록에서 제거
                for (key in keys) {
                    waitingClients[key]?.remove(waitingClient)
                }
                writer.write("*-1\r\n")  // Null Array
                writer.flush()
                return
            }
        }
        
        // 3. 깨어남 - 다시 확인하고 요소 가져오기
        for (key in keys) {
            val list = listStore[key]
            if (list != null && list.isNotEmpty()) {
                val element = list.removeAt(0)
                if (list.isEmpty()) listStore.remove(key)
                
                // 다른 키의 대기 목록에서 제거
                for (k in keys) {
                    waitingClients[k]?.remove(waitingClient)
                }
                
                writer.write("*2\r\n")
                writer.write("\$${key.length}\r\n$key\r\n")
                writer.write("\$${element.length}\r\n$element\r\n")
                writer.flush()
                return
            }
        }
    } finally {
        lock.unlock()
    }
}

// RPUSH/LPUSH에서 대기 중인 클라이언트 깨우기
"RPUSH" -> {
    val key = command.args[0]
    val elements = command.args.drop(1)
    
    lock.lock()
    try {
        // ... 기존 RPUSH 로직 (리스트에 추가) ...
        
        // 대기 중인 클라이언트 깨우기
        val waiting = waitingClients[key]?.firstOrNull()
        if (waiting != null) {
            waiting.condition.signal()
        }
    } finally {
        lock.unlock()
    }
}
```

---

### 🧪 테스트 방법

**터미널 1 (컨슈머):**
```bash
redis-cli BLPOP myqueue 0
# (대기 중... 아무것도 출력되지 않음)
```

**터미널 2 (프로듀서):**
```bash
redis-cli RPUSH myqueue "hello"
# 예상 출력: (integer) 1
```

**터미널 1 출력:**
```
1) "myqueue"
2) "hello"
```

**즉시 반환 테스트:**
```bash
# 먼저 데이터 추가
redis-cli RPUSH myqueue "existing"

# BLPOP 즉시 반환
redis-cli BLPOP myqueue 0
# 예상 출력:
# 1) "myqueue"
# 2) "existing"
```

**BRPOP 테스트:**
```bash
# 터미널 1
redis-cli BRPOP myqueue 0

# 터미널 2
redis-cli RPUSH myqueue "first" "second" "third"

# 터미널 1 출력 (마지막 요소)
# 1) "myqueue"
# 2) "third"
```

---

### 🤔 생각해볼 점

1. **공정성(Fairness)**: 여러 클라이언트가 같은 키를 BLPOP하고 있을 때, 누가 먼저 요소를 받아야 할까요? FIFO? 랜덤?

2. **여러 키 처리**: `BLPOP key1 key2 key3 0`에서 key2에 먼저 요소가 들어오면 어떻게 처리해야 할까요?

3. **연결 끊김**: 대기 중인 클라이언트의 연결이 끊어지면 어떻게 정리해야 할까요?

4. **단일 스레드 vs 멀티스레드**: Redis는 단일 스레드인데 어떻게 BLPOP을 구현할까요? (힌트: I/O 멀티플렉싱)

---

### ➡️ 다음 단계

Stage 11에서는 **타임아웃이 있는 블로킹 조회**를 더 완벽하게 구현합니다.
