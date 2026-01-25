# Redis Pub/Sub 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 채널 구독하기 (SUBSCRIBE) | ⬤○○ 쉬움 |
| **2** | **여러 채널 구독하기** | ⬤○○ 쉬움 |
| 3 | 구독 모드 진입하기 | ⬤⬤○ 보통 |
| 4 | 구독 모드에서 PING 처리 | ⬤○○ 쉬움 |
| 5 | 메시지 발행하기 (PUBLISH) | ⬤○○ 쉬움 |
| 6 | 메시지 전달하기 | ⬤⬤⬤ 어려움 |
| 7 | 구독 해제하기 (UNSUBSCRIBE) | ⬤⬤○ 보통 |

---

## Stage 2: 여러 채널 구독하기

### 🎯 목표

`SUBSCRIBE` 명령어가 **한 번에 여러 채널을 구독**할 수 있도록 확장합니다. 각 채널마다 구독 확인 메시지를 보내야 합니다.

---

### 📚 배경 지식

#### 다중 채널 구독

```
SUBSCRIBE <channel1> [channel2] [channel3] ...
```

한 번의 명령어로 여러 채널을 구독할 수 있습니다:

```bash
SUBSCRIBE news sports weather
# 응답 (3개의 메시지가 순서대로):
# 1) "subscribe" / "news" / 1
# 2) "subscribe" / "sports" / 2
# 3) "subscribe" / "weather" / 3
```

#### 응답 순서

각 채널마다 **개별 응답**이 전송됩니다. 구독 수는 **누적**됩니다:

```
SUBSCRIBE ch1 ch2 ch3

응답 1: ["subscribe", "ch1", 1]  # 첫 번째 구독 → 총 1개
응답 2: ["subscribe", "ch2", 2]  # 두 번째 구독 → 총 2개
응답 3: ["subscribe", "ch3", 3]  # 세 번째 구독 → 총 3개
```

#### 실무 활용

```bash
# 여러 사용자 알림 채널 동시 구독
SUBSCRIBE notifications:user:1 notifications:user:2 notifications:system

# 여러 채팅방 동시 입장
SUBSCRIBE chat:room:general chat:room:random chat:room:tech
```

---

### ✅ 통과 조건

- `SUBSCRIBE ch1 ch2 ch3` 형식으로 여러 채널을 구독할 수 있어야 합니다
- 각 채널마다 개별 구독 확인 메시지가 전송되어야 합니다
- 구독 수가 채널마다 1씩 증가해야 합니다

---

### 💡 힌트

인자로 받은 모든 채널에 대해 반복 처리합니다.

```kotlin
"SUBSCRIBE" -> {
    if (command.args.isEmpty()) {
        writer.write("-ERR wrong number of arguments for 'subscribe' command\r\n")
        writer.flush()
        return
    }
    
    val channels = command.args
    
    for (channel in channels) {
        // 채널에 구독자 추가
        channelSubscribers.computeIfAbsent(channel) { 
            ConcurrentHashMap.newKeySet() 
        }.add(writer)
        
        // 클라이언트의 구독 목록에 추가
        clientSubscriptions.computeIfAbsent(clientSocket) { 
            ConcurrentHashMap.newKeySet() 
        }.add(channel)
        
        // 현재까지의 구독 수 (누적)
        val subscriptionCount = clientSubscriptions[clientSocket]?.size ?: 1
        
        // 각 채널마다 구독 확인 메시지 전송
        writer.write("*3\r\n")
        writer.write("\$9\r\nsubscribe\r\n")
        writer.write("\$${channel.length}\r\n$channel\r\n")
        writer.write(":$subscriptionCount\r\n")
        writer.flush()
    }
}
```

---

### 🧪 테스트 방법

```bash
# 여러 채널 동시 구독
redis-cli SUBSCRIBE channel1 channel2 channel3
# 예상 출력:
# Reading messages... (press Ctrl-C to quit)
# 1) "subscribe"
# 2) "channel1"
# 3) (integer) 1
# 1) "subscribe"
# 2) "channel2"
# 3) (integer) 2
# 1) "subscribe"
# 2) "channel3"
# 3) (integer) 3

# 이미 구독 중인 채널을 다시 구독해도 에러 없음
# (중복 구독은 무시되거나 카운트가 증가하지 않음)
```

---

### 🤔 생각해볼 점

1. **중복 구독**: 같은 채널을 두 번 구독하면 어떻게 해야 할까요? 
   - 구독 수가 증가해야 할까요?
   - 아니면 무시해야 할까요?

2. **순서 보장**: 채널 구독 순서가 응답 순서와 일치해야 할까요?

---

### ➡️ 다음 단계

Stage 3에서는 **구독 모드**의 특별한 동작을 구현합니다. 구독 모드에서는 일부 명령어만 사용할 수 있습니다.
