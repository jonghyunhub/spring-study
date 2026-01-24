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
| 10 | 블로킹 조회 (BLPOP/BRPOP) | ⬤⬤○ 보통 |
| **11** | **타임아웃이 있는 블로킹 조회** | ⬤⬤○ 보통 |

---

## Stage 11: 타임아웃이 있는 블로킹 조회

### 🎯 목표

블로킹 명령어의 **타임아웃 처리**를 완벽하게 구현합니다. 지정된 시간 동안 요소가 추가되지 않으면 `nil`을 반환하고 대기를 종료합니다.

---

### 📚 배경 지식

#### 타임아웃의 필요성

무한 대기(`timeout=0`)는 위험할 수 있습니다:

- 클라이언트 연결이 영원히 점유됨
- 데드락 상황 발생 가능
- 리소스 누수

실무에서는 보통 적절한 타임아웃을 설정합니다:

```bash
# 최대 30초 대기
BLPOP job_queue 30

# 무한 대기 (주의해서 사용)
BLPOP job_queue 0
```

#### 타임아웃 동작

```
BLPOP myqueue 5
# 5초 동안 대기
# 5초 내에 요소가 들어오면 → [키, 값] 반환
# 5초가 지나면 → nil 반환
```

#### 응답 형식

| 상황 | 응답 |
|------|------|
| 요소를 받음 | `*2\r\n$key\r\n$value\r\n` |
| 타임아웃 | `*-1\r\n` (Null Array) |

#### 소수점 타임아웃 (Redis 6.0+)

Redis 6.0부터 소수점 타임아웃을 지원합니다:

```bash
BLPOP myqueue 0.5    # 500ms 대기
BLPOP myqueue 2.5    # 2.5초 대기
```

---

### ✅ 통과 조건

- `BLPOP <key> <timeout>` 에서 timeout 초 후에 nil을 반환해야 합니다
- 타임아웃 전에 요소가 들어오면 즉시 반환해야 합니다
- `timeout=0`이면 무한 대기해야 합니다
- 소수점 타임아웃을 지원해야 합니다 (옵션)

---

### 💡 힌트

타임아웃 처리의 핵심은 `Condition.await(timeout, unit)`입니다.

```kotlin
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

val lock = ReentrantLock()

fun handleBlpop(keys: List<String>, timeoutSeconds: Double, writer: java.io.BufferedWriter) {
    lock.lock()
    try {
        // 1. 즉시 사용 가능한 요소 확인
        for (key in keys) {
            val list = listStore[key]
            if (list != null && list.isNotEmpty()) {
                val element = list.removeAt(0)
                if (list.isEmpty()) listStore.remove(key)
                
                writeArrayResponse(writer, key, element)
                return
            }
        }
        
        // 2. 대기 설정
        val condition = lock.newCondition()
        val waitingClient = WaitingClient(keys, isLeft = true, condition)
        
        for (key in keys) {
            waitingClients.computeIfAbsent(key) { mutableListOf() }
                .add(waitingClient)
        }
        
        // 3. 타임아웃과 함께 대기
        val startTime = System.nanoTime()
        var remainingNanos = if (timeoutSeconds == 0.0) {
            Long.MAX_VALUE  // 무한 대기
        } else {
            (timeoutSeconds * 1_000_000_000).toLong()
        }
        
        while (remainingNanos > 0) {
            // 요소가 있는지 다시 확인
            for (key in keys) {
                val list = listStore[key]
                if (list != null && list.isNotEmpty()) {
                    val element = list.removeAt(0)
                    if (list.isEmpty()) listStore.remove(key)
                    
                    // 대기 목록에서 제거
                    cleanupWaitingClient(waitingClient, keys)
                    
                    writeArrayResponse(writer, key, element)
                    return
                }
            }
            
            // 대기
            if (timeoutSeconds == 0.0) {
                condition.await()  // 무한 대기
            } else {
                remainingNanos = condition.awaitNanos(remainingNanos)
            }
        }
        
        // 4. 타임아웃됨
        cleanupWaitingClient(waitingClient, keys)
        writer.write("*-1\r\n")  // Null Array
        
    } finally {
        lock.unlock()
        writer.flush()
    }
}

fun cleanupWaitingClient(client: WaitingClient, keys: List<String>) {
    for (key in keys) {
        waitingClients[key]?.remove(client)
    }
}

fun writeArrayResponse(writer: java.io.BufferedWriter, key: String, value: String) {
    writer.write("*2\r\n")
    writer.write("\$${key.length}\r\n$key\r\n")
    writer.write("\$${value.length}\r\n$value\r\n")
}

// RPUSH에서 대기 클라이언트 깨우기
fun notifyWaitingClients(key: String) {
    lock.lock()
    try {
        val waiters = waitingClients[key]
        if (waiters != null && waiters.isNotEmpty()) {
            // 첫 번째 대기자만 깨움 (FIFO)
            val first = waiters.first()
            first.condition.signal()
        }
    } finally {
        lock.unlock()
    }
}
```

---

### 🧪 테스트 방법

**타임아웃 테스트:**
```bash
# 3초 타임아웃으로 대기 (빈 리스트)
time redis-cli BLPOP emptyqueue 3
# 3초 후 출력:
# (nil)
# 
# real    0m3.xxx

# 소수점 타임아웃 (500ms)
time redis-cli BLPOP emptyqueue 0.5
# 0.5초 후 출력:
# (nil)
```

**타임아웃 전에 데이터 도착:**
```bash
# 터미널 1: 10초 타임아웃으로 대기
redis-cli BLPOP myqueue 10

# 터미널 2: 2초 후 데이터 추가
sleep 2 && redis-cli RPUSH myqueue "arrived"

# 터미널 1 출력 (약 2초 후):
# 1) "myqueue"
# 2) "arrived"
```

**무한 대기:**
```bash
# 터미널 1: 무한 대기
redis-cli BLPOP myqueue 0
# Ctrl+C로 중단하기 전까지 대기

# 터미널 2: 언제든 데이터 추가
redis-cli RPUSH myqueue "finally"
```

**여러 키 + 타임아웃:**
```bash
# 여러 키를 5초간 모니터링
redis-cli BLPOP queue1 queue2 queue3 5

# 다른 터미널에서
redis-cli RPUSH queue2 "from queue2"

# 출력:
# 1) "queue2"
# 2) "from queue2"
```

---

### 🤔 생각해볼 점

1. **Spurious Wakeup**: `condition.await()`는 이유 없이 깨어날 수 있습니다 (spurious wakeup). 코드에서 이를 어떻게 처리하고 있나요?

2. **정밀도**: 타임아웃이 정확히 N초일까요? 실제로는 약간의 오차가 있을 수 있습니다. 왜 그럴까요?

3. **클라이언트 끊김**: 클라이언트가 타임아웃 대기 중 연결을 끊으면? 서버 리소스는 어떻게 정리되어야 할까요?

4. **성능**: 10,000개의 클라이언트가 동시에 BLPOP을 한다면? 각각의 `Condition`이 필요할까요?

---

### 🎉 축하합니다!

모든 Lists Stage를 완료하셨습니다! 이제 여러분은 다음을 구현한 Redis List를 가지게 되었습니다:

- ✅ RPUSH / LPUSH (단일 & 다중 요소)
- ✅ LRANGE (양수 & 음수 인덱스)
- ✅ LLEN
- ✅ LPOP / RPOP (단일 & 다중 요소)
- ✅ BLPOP / BRPOP (타임아웃 지원)

---

### 🚀 더 나아가기 (보너스 챌린지)

| 챌린지 | 난이도 | 배울 수 있는 것 |
|--------|--------|-----------------|
| LINDEX | ⬤○○ | 인덱스로 요소 조회 |
| LSET | ⬤○○ | 인덱스 위치 요소 수정 |
| LINSERT | ⬤⬤○ | 특정 요소 앞/뒤에 삽입 |
| LREM | ⬤⬤○ | 값으로 요소 삭제 |
| LTRIM | ⬤⬤○ | 리스트 자르기 |
| LMOVE / RPOPLPUSH | ⬤⬤○ | 리스트 간 요소 이동 |
| LPOS | ⬤⬤○ | 값의 인덱스 찾기 |
| BLMOVE | ⬤⬤⬤ | 블로킹 리스트 이동 |

---

### 📖 다음 학습 주제

Redis의 다른 자료구조도 구현해보세요:

1. **Set**: SADD, SMEMBERS, SISMEMBER, SUNION, SINTER
2. **Hash**: HSET, HGET, HMSET, HGETALL
3. **Sorted Set**: ZADD, ZRANGE, ZRANK, ZSCORE
4. **Pub/Sub**: PUBLISH, SUBSCRIBE

각 자료구조는 고유한 사용 사례와 학습 포인트가 있습니다!
