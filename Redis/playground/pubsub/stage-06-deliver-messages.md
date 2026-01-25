# Redis Pub/Sub 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 채널 구독하기 (SUBSCRIBE) | ⬤○○ 쉬움 |
| 2 | 여러 채널 구독하기 | ⬤○○ 쉬움 |
| 3 | 구독 모드 진입하기 | ⬤⬤○ 보통 |
| 4 | 구독 모드에서 PING 처리 | ⬤○○ 쉬움 |
| 5 | 메시지 발행하기 (PUBLISH) | ⬤○○ 쉬움 |
| **6** | **메시지 전달하기** | ⬤⬤⬤ 어려움 |
| 7 | 구독 해제하기 (UNSUBSCRIBE) | ⬤⬤○ 보통 |

---

## Stage 6: 메시지 전달하기

### 🎯 목표

PUBLISH된 메시지를 **모든 구독자에게 실시간으로 전달**합니다. Pub/Sub의 핵심 기능입니다!

---

### 📚 배경 지식

#### 메시지 전달 흐름

```
Publisher                    Redis Server                 Subscribers
    │                             │                            │
    │ PUBLISH news "hello"        │                            │
    │ ─────────────────────────>  │                            │
    │                             │ ["message","news","hello"] │
    │                             │ ─────────────────────────> │ Subscriber 1
    │                             │ ─────────────────────────> │ Subscriber 2
    │                             │ ─────────────────────────> │ Subscriber 3
    │ :3 (구독자 수)               │                            │
    │ <─────────────────────────  │                            │
```

#### 메시지 형식

구독자가 받는 메시지는 **3개 요소의 배열**입니다:

```
*3\r\n
$7\r\nmessage\r\n     # 메시지 타입
$4\r\nnews\r\n        # 채널 이름
$5\r\nhello\r\n       # 메시지 내용
```

- 첫 번째: 항상 `"message"` (문자열)
- 두 번째: 채널 이름
- 세 번째: 메시지 내용

#### 비동기 전달

메시지는 구독자가 **요청하지 않아도** 서버에서 **푸시(push)** 됩니다. 구독자는 언제든 메시지를 받을 준비가 되어 있어야 합니다.

#### 동시성 고려사항

- 여러 Publisher가 동시에 같은 채널에 발행할 수 있음
- 한 구독자에게 메시지 전송이 느려도 다른 구독자에게 영향 없어야 함
- 전송 중 구독자 연결이 끊어질 수 있음

---

### ✅ 통과 조건

- PUBLISH 시 해당 채널의 모든 구독자에게 메시지가 전달되어야 합니다
- 메시지 형식은 `["message", channel, data]` 배열이어야 합니다
- 구독자 수를 정확히 반환해야 합니다
- 한 구독자의 문제가 다른 구독자에게 영향을 주지 않아야 합니다

---

### 💡 힌트

PUBLISH에서 모든 구독자에게 메시지를 전송합니다.

```kotlin
import java.io.BufferedWriter
import java.util.concurrent.ConcurrentHashMap

// 채널 -> 구독자 Writer 목록
val channelSubscribers = ConcurrentHashMap<String, MutableSet<BufferedWriter>>()

"PUBLISH" -> {
    if (command.args.size < 2) {
        writer.write("-ERR wrong number of arguments for 'publish' command\r\n")
        writer.flush()
        return
    }
    
    val channel = command.args[0]
    val message = command.args[1]
    
    val subscribers = channelSubscribers[channel]
    var deliveredCount = 0
    
    if (subscribers != null) {
        // 메시지 형식 미리 구성
        val messagePayload = buildString {
            append("*3\r\n")
            append("\$7\r\nmessage\r\n")
            append("\$${channel.length}\r\n$channel\r\n")
            append("\$${message.length}\r\n$message\r\n")
        }
        
        // 모든 구독자에게 전송
        val failedSubscribers = mutableListOf<BufferedWriter>()
        
        for (subscriberWriter in subscribers) {
            try {
                synchronized(subscriberWriter) {
                    subscriberWriter.write(messagePayload)
                    subscriberWriter.flush()
                }
                deliveredCount++
            } catch (e: Exception) {
                // 전송 실패한 구독자 기록 (연결 끊김 등)
                failedSubscribers.add(subscriberWriter)
            }
        }
        
        // 실패한 구독자 제거
        failedSubscribers.forEach { subscribers.remove(it) }
    }
    
    // 발행자에게 구독자 수 반환
    writer.write(":$deliveredCount\r\n")
    writer.flush()
}
```

**더 안전한 구현 (비동기):**

```kotlin
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

val publishExecutor: ExecutorService = Executors.newFixedThreadPool(4)

"PUBLISH" -> {
    val channel = command.args[0]
    val message = command.args[1]
    
    val subscribers = channelSubscribers[channel]?.toList() ?: emptyList()
    
    val messagePayload = buildString {
        append("*3\r\n")
        append("\$7\r\nmessage\r\n")
        append("\$${channel.length}\r\n$channel\r\n")
        append("\$${message.length}\r\n$message\r\n")
    }
    
    // 비동기로 메시지 전송 (Publisher가 블로킹되지 않음)
    for (subscriberWriter in subscribers) {
        publishExecutor.submit {
            try {
                synchronized(subscriberWriter) {
                    subscriberWriter.write(messagePayload)
                    subscriberWriter.flush()
                }
            } catch (e: Exception) {
                // 구독자 제거 처리
                channelSubscribers[channel]?.remove(subscriberWriter)
            }
        }
    }
    
    // 즉시 구독자 수 반환
    writer.write(":${subscribers.size}\r\n")
    writer.flush()
}
```

---

### 🧪 테스트 방법

**터미널 1 (구독자):**
```bash
redis-cli SUBSCRIBE news
# Reading messages... (press Ctrl-C to quit)
# 1) "subscribe"
# 2) "news"
# 3) (integer) 1
# (메시지 대기 중...)
```

**터미널 2 (구독자 2):**
```bash
redis-cli SUBSCRIBE news
# 1) "subscribe"
# 2) "news"
# 3) (integer) 1
```

**터미널 3 (발행자):**
```bash
redis-cli PUBLISH news "Hello, World!"
# (integer) 2
```

**터미널 1, 2 출력:**
```
1) "message"
2) "news"
3) "Hello, World!"
```

**연속 메시지 테스트:**
```bash
# 터미널 3에서 연속 발행
redis-cli PUBLISH news "First message"
redis-cli PUBLISH news "Second message"
redis-cli PUBLISH news "Third message"

# 터미널 1, 2에서 모든 메시지 수신 확인
```

**여러 채널 테스트:**
```bash
# 터미널 1: news 구독
redis-cli SUBSCRIBE news

# 터미널 2: sports 구독
redis-cli SUBSCRIBE sports

# 터미널 3: 각 채널에 발행
redis-cli PUBLISH news "News message"     # 터미널 1만 수신
redis-cli PUBLISH sports "Sports message" # 터미널 2만 수신
```

---

### 🤔 생각해볼 점

1. **순서 보장**: 여러 메시지를 빠르게 발행하면 구독자가 받는 순서가 보장될까요?

2. **전송 실패**: 구독자 중 한 명에게 전송이 실패하면 전체 PUBLISH가 실패해야 할까요?

3. **메모리**: 구독자가 메시지를 느리게 처리하면 서버 메모리에 영향이 있을까요?

4. **확장성**: 구독자가 10,000명이면 PUBLISH 성능은 어떻게 될까요? 어떻게 최적화할 수 있을까요?

---

### ➡️ 다음 단계

Stage 7에서는 **UNSUBSCRIBE** 명령어를 구현하여 채널 구독을 해제합니다.
