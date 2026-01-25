# Redis Pub/Sub 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| **1** | **채널 구독하기 (SUBSCRIBE)** | ⬤○○ 쉬움 |
| 2 | 여러 채널 구독하기 | ⬤○○ 쉬움 |
| 3 | 구독 모드 진입하기 | ⬤⬤○ 보통 |
| 4 | 구독 모드에서 PING 처리 | ⬤○○ 쉬움 |
| 5 | 메시지 발행하기 (PUBLISH) | ⬤○○ 쉬움 |
| 6 | 메시지 전달하기 | ⬤⬤⬤ 어려움 |
| 7 | 구독 해제하기 (UNSUBSCRIBE) | ⬤⬤○ 보통 |

---

## Stage 1: 채널 구독하기 (SUBSCRIBE)

### 🎯 목표

`SUBSCRIBE` 명령어를 구현하여 클라이언트가 **채널을 구독**할 수 있도록 합니다. Pub/Sub의 첫 번째 단계입니다!

---

### 📚 배경 지식

#### Pub/Sub란?

**Pub/Sub(Publish/Subscribe)** 는 메시지 브로커 패턴입니다:

- **Publisher(발행자)**: 메시지를 채널에 보냄
- **Subscriber(구독자)**: 채널의 메시지를 받음
- **Channel(채널)**: 메시지가 전달되는 통로

```
Publisher ──PUBLISH──> [channel] ──메시지──> Subscriber 1
                                  ──메시지──> Subscriber 2
                                  ──메시지──> Subscriber 3
```

#### 실무 활용 사례

```bash
# 실시간 알림
SUBSCRIBE notifications:user:123

# 채팅방
SUBSCRIBE chat:room:456

# 시스템 이벤트
SUBSCRIBE events:order:created
```

#### SUBSCRIBE 명령어

```
SUBSCRIBE <channel>
```

채널을 구독합니다. 구독 확인 메시지를 반환합니다.

#### 응답 형식 (Push 메시지)

SUBSCRIBE의 응답은 **3개 요소의 배열**입니다:

```
*3\r\n
$9\r\nsubscribe\r\n    # 메시지 타입
$7\r\nmychannel\r\n    # 채널 이름
:1\r\n                  # 현재 구독 중인 채널 수
```

---

### ✅ 통과 조건

- `SUBSCRIBE <channel>` 명령어가 구독 확인 메시지를 반환해야 합니다
- 응답은 `["subscribe", 채널명, 구독수]` 형식의 배열이어야 합니다
- 클라이언트의 구독 정보가 저장되어야 합니다

---

### 💡 힌트

채널별 구독자 목록과 클라이언트별 구독 채널을 관리합니다.

```kotlin
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.io.BufferedWriter

// 채널 -> 구독자(Writer) 목록
val channelSubscribers = ConcurrentHashMap<String, MutableSet<BufferedWriter>>()

// 클라이언트 -> 구독 중인 채널 목록
val clientSubscriptions = ConcurrentHashMap<Socket, MutableSet<String>>()

"SUBSCRIBE" -> {
    if (command.args.isEmpty()) {
        writer.write("-ERR wrong number of arguments for 'subscribe' command\r\n")
        writer.flush()
        return
    }
    
    val channel = command.args[0]
    
    // 채널에 구독자 추가
    channelSubscribers.computeIfAbsent(channel) { 
        ConcurrentHashMap.newKeySet() 
    }.add(writer)
    
    // 클라이언트의 구독 목록에 추가
    clientSubscriptions.computeIfAbsent(clientSocket) { 
        ConcurrentHashMap.newKeySet() 
    }.add(channel)
    
    val subscriptionCount = clientSubscriptions[clientSocket]?.size ?: 1
    
    // 구독 확인 메시지 전송
    writer.write("*3\r\n")
    writer.write("\$9\r\nsubscribe\r\n")
    writer.write("\$${channel.length}\r\n$channel\r\n")
    writer.write(":$subscriptionCount\r\n")
    writer.flush()
}
```

---

### 🧪 테스트 방법

```bash
# redis-cli로 구독
redis-cli SUBSCRIBE mychannel
# 예상 출력:
# Reading messages... (press Ctrl-C to quit)
# 1) "subscribe"
# 2) "mychannel"
# 3) (integer) 1

# 다른 채널 구독 (새 터미널)
redis-cli SUBSCRIBE news
# 예상 출력:
# 1) "subscribe"
# 2) "news"
# 3) (integer) 1
```

---

### 🤔 생각해볼 점

1. **동시성**: 여러 클라이언트가 동시에 같은 채널을 구독하면 어떻게 처리해야 할까요?

2. **메모리 관리**: 클라이언트가 연결을 끊으면 구독 정보는 어떻게 정리해야 할까요?

---

### ➡️ 다음 단계

Stage 2에서는 **한 번에 여러 채널을 구독**하는 기능을 구현합니다.
