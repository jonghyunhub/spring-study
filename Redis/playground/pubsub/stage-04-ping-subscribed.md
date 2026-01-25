# Redis Pub/Sub 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 채널 구독하기 (SUBSCRIBE) | ⬤○○ 쉬움 |
| 2 | 여러 채널 구독하기 | ⬤○○ 쉬움 |
| 3 | 구독 모드 진입하기 | ⬤⬤○ 보통 |
| **4** | **구독 모드에서 PING 처리** | ⬤○○ 쉬움 |
| 5 | 메시지 발행하기 (PUBLISH) | ⬤○○ 쉬움 |
| 6 | 메시지 전달하기 | ⬤⬤⬤ 어려움 |
| 7 | 구독 해제하기 (UNSUBSCRIBE) | ⬤⬤○ 보통 |

---

## Stage 4: 구독 모드에서 PING 처리

### 🎯 목표

구독 모드에서 `PING` 명령어를 처리합니다. 구독 모드의 PING은 **일반 모드와 응답 형식이 다릅니다!**

---

### 📚 배경 지식

#### 일반 모드 vs 구독 모드의 PING

**일반 모드:**
```bash
PING
# +PONG
```

**구독 모드:**
```bash
PING
# *2
# $4
# pong
# $0
# 
```

구독 모드에서는 모든 응답이 **Push 메시지 형식(배열)** 으로 전송됩니다.

#### PING with argument

PING에 인자를 주면 그 인자가 응답에 포함됩니다:

**일반 모드:**
```bash
PING "hello"
# $5
# hello
```

**구독 모드:**
```bash
PING "hello"
# *2
# $4
# pong
# $5
# hello
```

#### 왜 형식이 다를까?

구독 모드에서는 여러 종류의 메시지가 비동기로 도착합니다:
- 구독 확인: `["subscribe", channel, count]`
- 메시지: `["message", channel, data]`
- PONG: `["pong", argument]`

일관된 형식(배열)을 사용해야 클라이언트가 메시지 타입을 쉽게 구분할 수 있습니다.

---

### ✅ 통과 조건

- 구독 모드에서 `PING`이 `["pong", ""]` 형식으로 응답해야 합니다
- `PING "message"`가 `["pong", "message"]` 형식으로 응답해야 합니다
- 일반 모드에서는 기존처럼 `+PONG\r\n`으로 응답해야 합니다

---

### 💡 힌트

PING 처리에서 구독 모드 여부를 확인합니다.

```kotlin
"PING" -> {
    val argument = command.args.firstOrNull() ?: ""
    
    if (isInSubscribedMode(state)) {
        // 구독 모드: 배열 형식으로 응답
        writer.write("*2\r\n")
        writer.write("\$4\r\npong\r\n")
        writer.write("\$${argument.length}\r\n$argument\r\n")
    } else {
        // 일반 모드: Simple String 또는 Bulk String
        if (argument.isEmpty()) {
            writer.write("+PONG\r\n")
        } else {
            writer.write("\$${argument.length}\r\n$argument\r\n")
        }
    }
    writer.flush()
}
```

---

### 🧪 테스트 방법

```bash
# 일반 모드 PING
redis-cli PING
# 예상 출력: PONG

redis-cli PING "hello"
# 예상 출력: "hello"

# 구독 모드에서 PING 테스트 (netcat 사용)
nc localhost 6379

# 구독
*2
$9
SUBSCRIBE
$4
test
# 응답: ["subscribe", "test", 1]

# 구독 모드에서 PING
*1
$4
PING
# 예상 응답:
# *2
# $4
# pong
# $0
# 

# 인자와 함께 PING
*2
$4
PING
$5
hello
# 예상 응답:
# *2
# $4
# pong
# $5
# hello
```

**redis-cli에서 테스트 (제한적):**
```bash
redis-cli
127.0.0.1:6379> SUBSCRIBE test
Reading messages... (press Ctrl-C to quit)
1) "subscribe"
2) "test"
3) (integer) 1

# redis-cli 구독 모드에서는 직접 PING을 보내기 어려움
# 별도 클라이언트나 netcat 사용 권장
```

---

### 🤔 생각해볼 점

1. **연결 유지**: PING은 왜 구독 모드에서도 허용될까요?
   - TCP 연결이 살아있는지 확인
   - 방화벽/프록시의 유휴 타임아웃 방지

2. **프로토콜 일관성**: 구독 모드에서 모든 응답을 배열로 통일하면 어떤 장점이 있을까요?

---

### ➡️ 다음 단계

Stage 5에서는 **PUBLISH** 명령어를 구현하여 채널에 메시지를 발행합니다.
