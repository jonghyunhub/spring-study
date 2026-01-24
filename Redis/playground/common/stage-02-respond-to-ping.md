# Redis 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 포트에 바인딩하기 | ⬤○○ 매우 쉬움 |
| **2** | **PING에 응답하기** | ⬤○○ 쉬움 |
| 3 | 여러 PING에 응답하기 | ⬤○○ 쉬움 |
| 4 | 동시 클라이언트 처리하기 | ⬤⬤○ 보통 |
| 5 | ECHO 명령어 구현하기 | ⬤⬤○ 보통 |
| 6 | SET & GET 명령어 구현하기 | ⬤⬤○ 보통 |
| 7 | 키 만료(Expiry) 구현하기 | ⬤⬤○ 보통 |

---

## Stage 2: PING에 응답하기

### 🎯 목표

Redis의 가장 기본적인 명령어인 `PING`을 구현합니다. 클라이언트가 `PING`을 보내면 서버는 `PONG`으로 응답해야 합니다.

---

### 📚 배경 지식

#### RESP (Redis Serialization Protocol)

Redis는 클라이언트와 서버 간 통신에 **RESP**라는 자체 프로토콜을 사용합니다. RESP는 사람이 읽기 쉽고 파싱하기 간단하도록 설계되었습니다.

RESP에서 데이터 타입은 첫 번째 바이트로 구분됩니다:

| 첫 바이트 | 타입 | 예시 |
|-----------|------|------|
| `+` | Simple String | `+OK\r\n` |
| `-` | Error | `-ERR unknown command\r\n` |
| `:` | Integer | `:1000\r\n` |
| `$` | Bulk String | `$5\r\nhello\r\n` |
| `*` | Array | `*2\r\n$4\r\nPING\r\n$4\r\ntest\r\n` |

#### PING 명령어

`PING` 명령어는 서버가 살아있는지 확인하는 용도로 사용됩니다.

**요청:** 클라이언트가 보내는 PING (RESP Array 형식)
```
*1\r\n$4\r\nPING\r\n
```

**응답:** 서버가 보내는 PONG (Simple String 형식)
```
+PONG\r\n
```

---

### ✅ 통과 조건

- 클라이언트가 `PING` 명령어를 보내면 `+PONG\r\n`으로 응답해야 합니다
- RESP 프로토콜 형식을 준수해야 합니다

---

### 💡 힌트

1. 클라이언트 소켓에서 입력 스트림을 읽어 명령어를 파싱합니다
2. 명령어가 `PING`이면 `+PONG\r\n`을 출력 스트림에 씁니다

```kotlin
import java.net.ServerSocket

fun main() {
    val serverSocket = ServerSocket(6379)
    serverSocket.reuseAddress = true
    
    val clientSocket = serverSocket.accept()
    val reader = clientSocket.getInputStream().bufferedReader()
    val writer = clientSocket.getOutputStream().bufferedWriter()
    
    // TODO: reader에서 PING 명령어 읽기
    // TODO: writer에 +PONG\r\n 응답 쓰기
    
    writer.flush()
    clientSocket.close()
}
```

---

### 🧪 테스트 방법

```bash
# redis-cli로 PING 테스트
redis-cli PING 
# 예상 출력: PONG

# 또는 netcat으로 직접 RESP 프로토콜 전송
echo -e "*1\r\n\$4\r\nPING\r\n" | nc localhost 6379
# 예상 출력: +PONG
```

---

### ➡️ 다음 단계

Stage 3에서는 하나의 연결에서 여러 번의 `PING` 명령어를 처리할 수 있도록 개선합니다.
