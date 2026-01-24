# Redis 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 포트에 바인딩하기 | ⬤○○ 매우 쉬움 |
| 2 | PING에 응답하기 | ⬤○○ 쉬움 |
| 3 | 여러 PING에 응답하기 | ⬤○○ 쉬움 |
| 4 | 동시 클라이언트 처리하기 | ⬤⬤○ 보통 |
| **5** | **ECHO 명령어 구현하기** | ⬤⬤○ 보통 |
| 6 | SET & GET 명령어 구현하기 | ⬤⬤○ 보통 |
| 7 | 키 만료(Expiry) 구현하기 | ⬤⬤○ 보통 |

---

## Stage 5: ECHO 명령어 구현하기

### 🎯 목표

`ECHO` 명령어를 구현합니다. 이 명령어는 클라이언트가 보낸 메시지를 그대로 돌려보내는 간단한 명령어이지만, **인자(argument)를 파싱하는 방법**을 배울 수 있습니다.

---

### 📚 배경 지식

#### ECHO 명령어

```
ECHO <message>
```

클라이언트가 보낸 메시지를 그대로 반환합니다. 디버깅이나 연결 테스트에 사용됩니다.

#### RESP 배열 파싱 심화

인자가 있는 명령어는 RESP 배열 형식으로 전송됩니다:

```
*2\r\n        # 배열 요소 2개
$4\r\n        # 첫 번째 요소: 4바이트 문자열
ECHO\r\n      # "ECHO"
$5\r\n        # 두 번째 요소: 5바이트 문자열
hello\r\n     # "hello"
```

파싱 순서:
1. `*N` → 배열 요소 개수 확인
2. 각 요소에 대해:
   - `$M` → Bulk String 길이 확인
   - 다음 M 바이트 읽기

#### Bulk String 응답

`ECHO`의 응답은 Bulk String 형식입니다:

```
$5\r\n
hello\r\n
```

---

### ✅ 통과 조건

- `ECHO <message>` 명령어를 처리해야 합니다
- 응답은 Bulk String 형식(`$<length>\r\n<data>\r\n`)이어야 합니다
- 기존 `PING` 명령어도 계속 동작해야 합니다

---

### 💡 힌트

RESP 배열을 파싱하는 함수를 만들어 명령어와 인자를 추출합니다.

```kotlin
data class Command(val name: String, val args: List<String>)

fun parseCommand(reader: java.io.BufferedReader): Command? {
    val line = reader.readLine() ?: return null
    
    if (!line.startsWith("*")) {
        // 단순 명령어 처리 (inline command)
        return Command(line.uppercase(), emptyList())
    }
    
    val count = line.substring(1).toInt()
    val parts = mutableListOf<String>()
    
    repeat(count) {
        val bulkHeader = reader.readLine()  // $N
        val length = bulkHeader.substring(1).toInt()
        val data = reader.readLine()        // 실제 데이터
        parts.add(data)
    }
    
    return Command(parts[0].uppercase(), parts.drop(1))
}

fun handleCommand(command: Command, writer: java.io.BufferedWriter) {
    when (command.name) {
        "PING" -> {
            writer.write("+PONG\r\n")
        }
        "ECHO" -> {
            val message = command.args.firstOrNull() ?: ""
            writer.write("\$${message.length}\r\n$message\r\n")
        }
        else -> {
            writer.write("-ERR unknown command '${command.name}'\r\n")
        }
    }
    writer.flush()
}
```

---

### 🧪 테스트 방법

```bash
# redis-cli로 ECHO 테스트
redis-cli ECHO "hello world"
# 예상 출력: "hello world"

redis-cli ECHO redis
# 예상 출력: "redis"

# PING도 여전히 동작해야 함
redis-cli PING
# 예상 출력: PONG
```

---

### 🤔 생각해볼 점

1. **명령어 라우팅**: 명령어가 많아지면 `when` 문이 길어집니다. 어떻게 구조화할 수 있을까요?
   - Command 패턴
   - Map<String, CommandHandler>

2. **에러 처리**: 인자가 없거나 잘못된 경우 어떻게 처리해야 할까요?

---

### ➡️ 다음 단계

Stage 6에서는 Redis의 핵심 명령어인 `SET`과 `GET`을 구현하며, **데이터 저장소**를 만듭니다.
