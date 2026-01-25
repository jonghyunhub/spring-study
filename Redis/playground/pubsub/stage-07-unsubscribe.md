# Redis Pub/Sub 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 채널 구독하기 (SUBSCRIBE) | ⬤○○ 쉬움 |
| 2 | 여러 채널 구독하기 | ⬤○○ 쉬움 |
| 3 | 구독 모드 진입하기 | ⬤⬤○ 보통 |
| 4 | 구독 모드에서 PING 처리 | ⬤○○ 쉬움 |
| 5 | 메시지 발행하기 (PUBLISH) | ⬤○○ 쉬움 |
| 6 | 메시지 전달하기 | ⬤⬤⬤ 어려움 |
| **7** | **구독 해제하기 (UNSUBSCRIBE)** | ⬤⬤○ 보통 |

---

## Stage 7: 구독 해제하기 (UNSUBSCRIBE)

### 🎯 목표

`UNSUBSCRIBE` 명령어를 구현하여 **채널 구독을 해제**할 수 있도록 합니다. 모든 채널을 해제하면 구독 모드에서 빠져나옵니다.

---

### 📚 배경 지식

#### UNSUBSCRIBE 명령어

```
UNSUBSCRIBE [channel1] [channel2] ...
```

지정된 채널의 구독을 해제합니다.

- 채널을 지정하면: 해당 채널만 해제
- 채널을 지정하지 않으면: **모든 채널** 해제

#### 응답 형식

구독 확인과 동일한 형식이지만 타입이 `"unsubscribe"`:

```
*3\r\n
$11\r\nunsubscribe\r\n   # 메시지 타입
$7\r\nmychannel\r\n       # 채널 이름
:0\r\n                     # 남은 구독 채널 수
```

#### 다중 채널 해제

여러 채널을 한 번에 해제할 수 있습니다:

```bash
UNSUBSCRIBE ch1 ch2 ch3
# 응답 (3개):
# ["unsubscribe", "ch1", 2]
# ["unsubscribe", "ch2", 1]
# ["unsubscribe", "ch3", 0]
```

#### 모든 채널 해제

인자 없이 UNSUBSCRIBE를 호출하면 모든 채널을 해제합니다:

```bash
SUBSCRIBE ch1 ch2 ch3
# 3개 채널 구독 중

UNSUBSCRIBE
# 응답 (3개):
# ["unsubscribe", "ch1", 2]
# ["unsubscribe", "ch2", 1]
# ["unsubscribe", "ch3", 0]
```

#### 구독 모드 종료

남은 구독 채널이 0이 되면 구독 모드가 종료됩니다:

```bash
SUBSCRIBE mychannel
# 구독 모드 진입

UNSUBSCRIBE mychannel
# ["unsubscribe", "mychannel", 0]
# 구독 모드 종료!

GET foo
# 이제 일반 명령어 사용 가능
```

---

### ✅ 통과 조건

- `UNSUBSCRIBE <channel>` 명령어로 특정 채널을 해제할 수 있어야 합니다
- `UNSUBSCRIBE` (인자 없이)로 모든 채널을 해제할 수 있어야 합니다
- 응답은 `["unsubscribe", 채널명, 남은구독수]` 형식이어야 합니다
- 모든 채널 해제 후 구독 모드가 종료되어야 합니다
- 구독하지 않은 채널을 해제해도 에러가 발생하지 않아야 합니다

---

### 💡 힌트

```kotlin
"UNSUBSCRIBE" -> {
    val channels = if (command.args.isEmpty()) {
        // 인자 없으면 모든 구독 채널 해제
        state.subscribedChannels.toList()
    } else {
        command.args
    }
    
    // 구독 중인 채널이 없는 경우에도 응답 필요
    if (channels.isEmpty()) {
        writer.write("*3\r\n")
        writer.write("\$11\r\nunsubscribe\r\n")
        writer.write("\$0\r\n\r\n")  // 빈 채널명
        writer.write(":0\r\n")
        writer.flush()
        return
    }
    
    for (channel in channels) {
        // 채널 구독자 목록에서 제거
        channelSubscribers[channel]?.remove(writer)
        
        // 클라이언트의 구독 목록에서 제거
        state.subscribedChannels.remove(channel)
        
        // 남은 구독 수
        val remainingCount = state.subscribedChannels.size
        
        // 구독 해제 확인 메시지
        writer.write("*3\r\n")
        writer.write("\$11\r\nunsubscribe\r\n")
        writer.write("\$${channel.length}\r\n$channel\r\n")
        writer.write(":$remainingCount\r\n")
        writer.flush()
    }
    
    // 모든 구독이 해제되면 구독 모드 자동 종료
    // (subscribedChannels가 비어있으면 isInSubscribedMode가 false)
}
```

**연결 종료 시 정리:**

```kotlin
fun cleanupClient(clientSocket: Socket, writer: BufferedWriter, state: ClientState) {
    // 클라이언트가 연결을 끊을 때 모든 구독 정리
    for (channel in state.subscribedChannels) {
        channelSubscribers[channel]?.remove(writer)
        
        // 구독자가 없는 채널은 정리
        if (channelSubscribers[channel]?.isEmpty() == true) {
            channelSubscribers.remove(channel)
        }
    }
    state.subscribedChannels.clear()
}
```

---

### 🧪 테스트 방법

```bash
# redis-cli 대화형 모드
redis-cli

# 여러 채널 구독
127.0.0.1:6379> SUBSCRIBE ch1 ch2 ch3
1) "subscribe"
2) "ch1"
3) (integer) 1
1) "subscribe"
2) "ch2"
3) (integer) 2
1) "subscribe"
2) "ch3"
3) (integer) 3

# 특정 채널 해제 (Ctrl+C로 나가서 새로 시작)
# netcat으로 테스트하는 것이 더 용이

nc localhost 6379

# 구독
*2
$9
SUBSCRIBE
$3
ch1

*2
$9
SUBSCRIBE
$3
ch2

# ch1 해제
*2
$11
UNSUBSCRIBE
$3
ch1
# 응답: ["unsubscribe", "ch1", 1]

# 남은 ch2 해제
*2
$11
UNSUBSCRIBE
$3
ch2
# 응답: ["unsubscribe", "ch2", 0]

# 이제 일반 명령어 가능
*2
$3
GET
$3
foo
# 응답: $-1 (정상 응답, 에러 아님)
```

**모든 채널 한 번에 해제:**
```bash
nc localhost 6379

# 여러 채널 구독
*4
$9
SUBSCRIBE
$3
ch1
$3
ch2
$3
ch3

# 모든 채널 해제 (인자 없이)
*1
$11
UNSUBSCRIBE
# 응답: 3개의 unsubscribe 메시지
```

**구독하지 않은 채널 해제:**
```bash
redis-cli SUBSCRIBE ch1
# ...구독 중...

# 다른 터미널에서 (테스트용 클라이언트)
# UNSUBSCRIBE nonexistent_channel
# 에러 없이 응답: ["unsubscribe", "nonexistent_channel", N]
```

---

### 🤔 생각해볼 점

1. **부분 해제**: `SUBSCRIBE a b c` 후 `UNSUBSCRIBE b`하면 a, c는 계속 구독 중일까요?

2. **중복 해제**: 같은 채널을 두 번 UNSUBSCRIBE하면 어떻게 될까요?

3. **경쟁 조건**: UNSUBSCRIBE와 동시에 PUBLISH가 발생하면?

---

### 🎉 축하합니다!

모든 Pub/Sub Stage를 완료하셨습니다! 이제 여러분은 다음을 구현한 Redis Pub/Sub을 가지게 되었습니다:

- ✅ SUBSCRIBE (단일 & 다중 채널)
- ✅ 구독 모드 (제한된 명령어)
- ✅ 구독 모드 PING
- ✅ PUBLISH (메시지 발행)
- ✅ 메시지 전달 (실시간 푸시)
- ✅ UNSUBSCRIBE (단일 & 전체)

---

### 🚀 더 나아가기 (보너스 챌린지)

| 챌린지 | 난이도 | 배울 수 있는 것 |
|--------|--------|-----------------|
| PSUBSCRIBE | ⬤⬤○ | 패턴 매칭 구독 (`news.*`) |
| PUNSUBSCRIBE | ⬤⬤○ | 패턴 구독 해제 |
| PUBSUB CHANNELS | ⬤○○ | 활성 채널 목록 조회 |
| PUBSUB NUMSUB | ⬤○○ | 채널별 구독자 수 조회 |
| PUBSUB NUMPAT | ⬤○○ | 패턴 구독 수 조회 |
| Sharded Pub/Sub | ⬤⬤⬤ | 클러스터 환경 Pub/Sub |

---

### 📖 학습 포인트 정리

1. **Pub/Sub은 Fire-and-Forget** - 메시지 전달 보장 없음
2. **구독 모드는 특수 상태** - 제한된 명령어만 사용 가능
3. **실시간 푸시** - 클라이언트가 요청하지 않아도 메시지 수신
4. **메시지 저장 없음** - 구독 전 발행된 메시지는 받을 수 없음
5. **확장성 고려** - 많은 구독자에게 효율적 전달 필요

---

### 📚 Pub/Sub vs 다른 메시징 패턴

| 특성 | Redis Pub/Sub | Redis Streams | 메시지 큐 (RabbitMQ 등) |
|------|---------------|---------------|------------------------|
| 메시지 저장 | ❌ | ✅ | ✅ |
| 전달 보장 | ❌ | ✅ | ✅ |
| 재처리 | ❌ | ✅ | ✅ |
| 다중 컨슈머 | ✅ (브로드캐스트) | ✅ (컨슈머 그룹) | ✅ |
| 사용 사례 | 실시간 알림, 채팅 | 이벤트 소싱, 로그 | 작업 큐, 비동기 처리 |

Pub/Sub은 단순하고 빠르지만, 메시지 손실이 허용되는 경우에만 사용하세요!
