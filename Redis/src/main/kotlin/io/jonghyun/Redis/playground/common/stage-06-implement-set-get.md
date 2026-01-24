# Redis 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 포트에 바인딩하기 | ⬤○○ 매우 쉬움 |
| 2 | PING에 응답하기 | ⬤○○ 쉬움 |
| 3 | 여러 PING에 응답하기 | ⬤○○ 쉬움 |
| 4 | 동시 클라이언트 처리하기 | ⬤⬤○ 보통 |
| 5 | ECHO 명령어 구현하기 | ⬤⬤○ 보통 |
| **6** | **SET & GET 명령어 구현하기** | ⬤⬤○ 보통 |
| 7 | 키 만료(Expiry) 구현하기 | ⬤⬤○ 보통 |

---

## Stage 6: SET & GET 명령어 구현하기

### 🎯 목표

Redis의 가장 핵심적인 명령어인 `SET`과 `GET`을 구현합니다. 이 단계를 완료하면 드디어 **데이터를 저장하고 조회**할 수 있는 진짜 키-값 저장소가 됩니다!

---

### 📚 배경 지식

#### SET 명령어

```
SET <key> <value>
```

키에 값을 저장합니다. 키가 이미 존재하면 덮어씁니다.

**응답:** `+OK\r\n` (Simple String)

#### GET 명령어

```
GET <key>
```

키에 저장된 값을 반환합니다.

**응답:**
- 키가 존재하는 경우: `$<length>\r\n<value>\r\n` (Bulk String)
- 키가 존재하지 않는 경우: `$-1\r\n` (Null Bulk String)

#### RESP 프로토콜 예시

**SET 요청:**
```
*3\r\n$3\r\nSET\r\n$4\r\nname\r\n$5\r\nhello\r\n
```

**GET 요청:**
```
*2\r\n$3\r\nGET\r\n$4\r\nname\r\n
```

#### 동시성과 데이터 저장소

여러 스레드에서 동시에 데이터에 접근하므로 **스레드 안전(Thread-Safe)** 한 자료구조가 필요합니다.

```kotlin
// ❌ 스레드 안전하지 않음
val store = mutableMapOf<String, String>()

// ✅ 스레드 안전
val store = java.util.concurrent.ConcurrentHashMap<String, String>()
```

---

### ✅ 통과 조건

- `SET <key> <value>` 명령어로 데이터를 저장할 수 있어야 합니다
- `GET <key>` 명령어로 저장된 데이터를 조회할 수 있어야 합니다
- 존재하지 않는 키에 대한 `GET`은 `$-1\r\n`을 반환해야 합니다
- 여러 클라이언트가 동시에 접근해도 데이터가 손상되지 않아야 합니다

---

### 💡 힌트

전역 저장소를 만들고 각 명령어에서 접근합니다.

```kotlin
import java.util.concurrent.ConcurrentHashMap

// 전역 데이터 저장소
val dataStore = ConcurrentHashMap<String, String>()

fun handleCommand(command: Command, writer: java.io.BufferedWriter) {
    when (command.name) {
        "PING" -> {
            writer.write("+PONG\r\n")
        }
        "ECHO" -> {
            val message = command.args.firstOrNull() ?: ""
            writer.write("\$${message.length}\r\n$message\r\n")
        }
        "SET" -> {
            if (command.args.size < 2) {
                writer.write("-ERR wrong number of arguments for 'set' command\r\n")
            } else {
                val key = command.args[0]
                val value = command.args[1]
                dataStore[key] = value
                writer.write("+OK\r\n")
            }
        }
        "GET" -> {
            if (command.args.isEmpty()) {
                writer.write("-ERR wrong number of arguments for 'get' command\r\n")
            } else {
                val key = command.args[0]
                val value = dataStore[key]
                if (value == null) {
                    writer.write("\$-1\r\n")  // Null Bulk String
                } else {
                    writer.write("\$${value.length}\r\n$value\r\n")
                }
            }
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
# SET과 GET 기본 테스트
redis-cli SET mykey "Hello Redis"
# 예상 출력: OK

redis-cli GET mykey
# 예상 출력: "Hello Redis"

# 존재하지 않는 키 조회
redis-cli GET nonexistent
# 예상 출력: (nil)

# 키 덮어쓰기 테스트
redis-cli SET mykey "New Value"
# 예상 출력: OK

redis-cli GET mykey
# 예상 출력: "New Value"
```

**동시성 테스트:**
```bash
# 여러 클라이언트에서 동시에 SET/GET
for i in {1..10}; do
    redis-cli SET "key$i" "value$i" &
done
wait

for i in {1..10}; do
    redis-cli GET "key$i"
done
# 각각 value1 ~ value10이 출력되어야 함
```

---

### 🤔 생각해볼 점

1. **메모리 관리**: 데이터가 계속 쌓이면 메모리가 부족해집니다. Redis는 이를 어떻게 해결할까요?
   - maxmemory 설정
   - LRU/LFU 기반 자동 삭제
   - TTL (다음 단계에서 구현!)

2. **데이터 타입**: 현재는 문자열만 저장합니다. Redis의 다양한 데이터 타입(List, Set, Hash, Sorted Set)은 어떻게 구현할 수 있을까요?

3. **영속성**: 서버가 재시작되면 데이터가 사라집니다. Redis의 RDB/AOF 방식은 무엇일까요?

---

### ➡️ 다음 단계

Stage 7에서는 `SET` 명령어에 **만료 시간(TTL)** 옵션을 추가합니다. 키가 자동으로 삭제되는 기능을 구현해봅시다!
