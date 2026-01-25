# Redis Pub/Sub 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 채널 구독하기 (SUBSCRIBE) | ⬤○○ 쉬움 |
| 2 | 여러 채널 구독하기 | ⬤○○ 쉬움 |
| **3** | **구독 모드 진입하기** | ⬤⬤○ 보통 |
| 4 | 구독 모드에서 PING 처리 | ⬤○○ 쉬움 |
| 5 | 메시지 발행하기 (PUBLISH) | ⬤○○ 쉬움 |
| 6 | 메시지 전달하기 | ⬤⬤⬤ 어려움 |
| 7 | 구독 해제하기 (UNSUBSCRIBE) | ⬤⬤○ 보통 |

---

## Stage 3: 구독 모드 진입하기

### 🎯 목표

클라이언트가 채널을 구독하면 **구독 모드(Subscribed Mode)** 에 진입합니다. 구독 모드에서는 **제한된 명령어만** 사용할 수 있습니다.

---

### 📚 배경 지식

#### 구독 모드란?

SUBSCRIBE를 실행하면 클라이언트는 **구독 모드**에 들어갑니다. 이 모드에서는:

- 메시지를 **수신**하기 위해 대기
- 대부분의 일반 명령어 **사용 불가**
- 특정 명령어만 허용

#### 구독 모드에서 허용되는 명령어

| 명령어 | 설명 |
|--------|------|
| `SUBSCRIBE` | 추가 채널 구독 |
| `UNSUBSCRIBE` | 채널 구독 해제 |
| `PSUBSCRIBE` | 패턴으로 구독 |
| `PUNSUBSCRIBE` | 패턴 구독 해제 |
| `PING` | 연결 확인 |
| `QUIT` | 연결 종료 |

#### 허용되지 않는 명령어

```bash
# 구독 모드에서
SUBSCRIBE mychannel
# OK

GET mykey
# (error) ERR Can't execute 'GET': only (P|S)SUBSCRIBE / (P|S)UNSUBSCRIBE / PING / QUIT are allowed in this context

SET foo bar
# (error) ERR Can't execute 'SET': only (P|S)SUBSCRIBE / (P|S)UNSUBSCRIBE / PING / QUIT are allowed in this context
```

#### 구독 모드 종료

모든 채널 구독을 해제하면 구독 모드가 종료됩니다:

```bash
SUBSCRIBE ch1 ch2
# 구독 모드 진입

UNSUBSCRIBE ch1
# 아직 ch2 구독 중 → 구독 모드 유지

UNSUBSCRIBE ch2
# 구독 채널 0개 → 구독 모드 종료

GET mykey
# 이제 일반 명령어 사용 가능
```

---

### ✅ 통과 조건

- SUBSCRIBE 후 구독 모드에 진입해야 합니다
- 구독 모드에서 허용되지 않는 명령어는 에러를 반환해야 합니다
- 에러 메시지에 어떤 명령어인지 표시되어야 합니다

---

### 💡 힌트

클라이언트 상태에 구독 모드 여부를 추가합니다.

```kotlin
data class ClientState(
    var inTransaction: Boolean = false,
    val queuedCommands: MutableList<Command> = mutableListOf(),
    val subscribedChannels: MutableSet<String> = mutableSetOf()  // 추가
)

// 구독 모드 확인
fun isInSubscribedMode(state: ClientState): Boolean {
    return state.subscribedChannels.isNotEmpty()
}

// 구독 모드에서 허용된 명령어
val ALLOWED_IN_SUBSCRIBED_MODE = setOf(
    "SUBSCRIBE", "UNSUBSCRIBE", 
    "PSUBSCRIBE", "PUNSUBSCRIBE", 
    "PING", "QUIT"
)

fun handleCommand(command: Command, writer: BufferedWriter, state: ClientState) {
    // 구독 모드 체크
    if (isInSubscribedMode(state) && command.name !in ALLOWED_IN_SUBSCRIBED_MODE) {
        writer.write("-ERR Can't execute '${command.name}': only (P|S)SUBSCRIBE / (P|S)UNSUBSCRIBE / PING / QUIT are allowed in this context\r\n")
        writer.flush()
        return
    }
    
    when (command.name) {
        "SUBSCRIBE" -> {
            for (channel in command.args) {
                // 채널 구독 처리
                channelSubscribers.computeIfAbsent(channel) { 
                    ConcurrentHashMap.newKeySet() 
                }.add(writer)
                
                state.subscribedChannels.add(channel)  // 상태에 추가
                
                val subscriptionCount = state.subscribedChannels.size
                
                writer.write("*3\r\n")
                writer.write("\$9\r\nsubscribe\r\n")
                writer.write("\$${channel.length}\r\n$channel\r\n")
                writer.write(":$subscriptionCount\r\n")
                writer.flush()
            }
        }
        // ... 다른 명령어들
    }
}
```

---

### 🧪 테스트 방법

```bash
# 터미널 1: 구독 모드 진입
redis-cli

127.0.0.1:6379> SUBSCRIBE mychannel
Reading messages... (press Ctrl-C to quit)
1) "subscribe"
2) "mychannel"
3) (integer) 1

# 이 상태에서 일반 명령어 시도 (redis-cli에서는 직접 테스트 어려움)
# 별도 클라이언트 구현 또는 netcat으로 테스트

# netcat으로 테스트
nc localhost 6379

# SUBSCRIBE 전송
*2
$9
SUBSCRIBE
$9
mychannel
# 응답: subscribe 메시지

# GET 전송 (구독 모드에서)
*2
$3
GET
$5
mykey
# 예상 응답: -ERR Can't execute 'GET': only (P|S)SUBSCRIBE / (P|S)UNSUBSCRIBE / PING / QUIT are allowed in this context
```

---

### 🤔 생각해볼 점

1. **왜 제한할까?**: 구독 모드에서 왜 일반 명령어를 제한할까요? 
   - 클라이언트가 메시지 수신에 집중하도록
   - 응답과 푸시 메시지가 섞이는 것 방지

2. **RESP3와 차이**: Redis 6.0+의 RESP3 프로토콜에서는 Push 메시지가 별도 채널로 전송되어 구독 모드 제한이 완화됩니다.

---

### ➡️ 다음 단계

Stage 4에서는 구독 모드에서 **PING 명령어를 처리**합니다. 구독 모드의 PING은 응답 형식이 다릅니다!
