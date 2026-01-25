# Redis Pub/Sub 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 채널 구독하기 (SUBSCRIBE) | ⬤○○ 쉬움 |
| 2 | 여러 채널 구독하기 | ⬤○○ 쉬움 |
| 3 | 구독 모드 진입하기 | ⬤⬤○ 보통 |
| 4 | 구독 모드에서 PING 처리 | ⬤○○ 쉬움 |
| **5** | **메시지 발행하기 (PUBLISH)** | ⬤○○ 쉬움 |
| 6 | 메시지 전달하기 | ⬤⬤⬤ 어려움 |
| 7 | 구독 해제하기 (UNSUBSCRIBE) | ⬤⬤○ 보통 |

---

## Stage 5: 메시지 발행하기 (PUBLISH)

### 🎯 목표

`PUBLISH` 명령어를 구현하여 **채널에 메시지를 발행**할 수 있도록 합니다. 이 단계에서는 발행 명령어의 기본 동작(수신자 수 반환)만 구현합니다.

---

### 📚 배경 지식

#### PUBLISH 명령어

```
PUBLISH <channel> <message>
```

지정된 채널에 메시지를 발행합니다. **메시지를 수신한 클라이언트 수**를 반환합니다.

```bash
PUBLISH news "Breaking news!"
# (integer) 3    # 3명의 구독자에게 전달됨
```

#### 반환값

- 해당 채널을 구독 중인 클라이언트 수
- 구독자가 없으면 `0`
- 패턴 구독자(PSUBSCRIBE)도 포함

```bash
# 구독자 없는 채널
PUBLISH empty_channel "hello"
# (integer) 0

# 구독자 있는 채널
PUBLISH popular_channel "hello"
# (integer) 42
```

#### Publisher는 구독 모드가 아님

**중요**: PUBLISH를 실행하는 클라이언트는 구독 모드가 아닙니다. 일반 명령어를 계속 사용할 수 있습니다.

```bash
PUBLISH news "message"   # 발행
SET foo "bar"            # 일반 명령어도 OK
GET foo                  # OK
```

#### 실무 활용

```bash
# 실시간 알림
PUBLISH notifications:user:123 '{"type":"new_message","from":"user456"}'

# 캐시 무효화
PUBLISH cache:invalidate "product:789"

# 시스템 이벤트
PUBLISH events:order '{"event":"created","order_id":12345}'
```

---

### ✅ 통과 조건

- `PUBLISH <channel> <message>` 명령어가 동작해야 합니다
- 해당 채널의 구독자 수를 Integer로 반환해야 합니다
- 구독자가 없으면 `0`을 반환해야 합니다
- PUBLISH 후에도 일반 명령어를 사용할 수 있어야 합니다

---

### 💡 힌트

채널의 구독자 수를 세어 반환합니다. (실제 메시지 전달은 다음 단계에서 구현)

```kotlin
"PUBLISH" -> {
    if (command.args.size < 2) {
        writer.write("-ERR wrong number of arguments for 'publish' command\r\n")
        writer.flush()
        return
    }
    
    val channel = command.args[0]
    val message = command.args[1]
    
    // 해당 채널의 구독자 수 확인
    val subscribers = channelSubscribers[channel]
    val subscriberCount = subscribers?.size ?: 0
    
    // TODO: 실제 메시지 전달 (다음 단계)
    // subscribers?.forEach { subscriberWriter ->
    //     // 메시지 전송
    // }
    
    // 구독자 수 반환
    writer.write(":$subscriberCount\r\n")
    writer.flush()
}
```

---

### 🧪 테스트 방법

```bash
# 구독자 없는 채널에 발행
redis-cli PUBLISH nonexistent "hello"
# 예상 출력: (integer) 0

# 터미널 1: 구독
redis-cli SUBSCRIBE news
# (구독 대기 중...)

# 터미널 2: 발행
redis-cli PUBLISH news "Breaking news!"
# 예상 출력: (integer) 1

# 터미널 3: 추가 구독
redis-cli SUBSCRIBE news

# 터미널 2: 다시 발행
redis-cli PUBLISH news "More news!"
# 예상 출력: (integer) 2

# 발행자는 일반 명령어 사용 가능
redis-cli
127.0.0.1:6379> PUBLISH test "message"
(integer) 0
127.0.0.1:6379> SET foo "bar"
OK
127.0.0.1:6379> GET foo
"bar"
```

---

### 🤔 생각해볼 점

1. **Fire and Forget**: PUBLISH는 메시지가 실제로 처리되었는지 알 수 없습니다. 이것이 문제가 될 수 있는 상황은?

2. **메시지 손실**: 구독자가 메시지를 받기 전에 연결이 끊어지면? 메시지는 어디로 갈까요?

3. **vs Message Queue**: Pub/Sub과 메시지 큐(예: Redis Streams, RabbitMQ)의 차이점은?

---

### ➡️ 다음 단계

Stage 6에서는 PUBLISH된 메시지를 **실제로 구독자에게 전달**합니다. 가장 중요한 단계입니다!
