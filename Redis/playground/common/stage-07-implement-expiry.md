# Redis 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 포트에 바인딩하기 | ⬤○○ 매우 쉬움 |
| 2 | PING에 응답하기 | ⬤○○ 쉬움 |
| 3 | 여러 PING에 응답하기 | ⬤○○ 쉬움 |
| 4 | 동시 클라이언트 처리하기 | ⬤⬤○ 보통 |
| 5 | ECHO 명령어 구현하기 | ⬤⬤○ 보통 |
| 6 | SET & GET 명령어 구현하기 | ⬤⬤○ 보통 |
| **7** | **키 만료(Expiry) 구현하기** | ⬤⬤○ 보통 |

---

## Stage 7: 키 만료(Expiry) 구현하기

### 🎯 목표

`SET` 명령어에 **PX 옵션**을 추가하여 키의 만료 시간을 설정할 수 있도록 합니다. 만료된 키는 `GET` 요청 시 존재하지 않는 것처럼 처리됩니다.

---

### 📚 배경 지식

#### TTL (Time To Live)

TTL은 키가 자동으로 삭제되기까지의 시간입니다. Redis에서 TTL은 다음과 같은 용도로 활용됩니다:

- **세션 관리**: 로그인 세션 30분 후 자동 만료
- **캐시**: 데이터 5분간 캐싱 후 갱신
- **Rate Limiting**: 1분당 100회 요청 제한
- **임시 데이터**: 이메일 인증 코드 10분 유효

#### SET 명령어 옵션

```
SET <key> <value> [PX milliseconds]
```

- `PX`: 밀리초 단위로 만료 시간 설정

**예시:**
```bash
SET session:123 "user_data" PX 60000  # 60초(60000ms) 후 만료
```

#### RESP 프로토콜 예시

```
*5\r\n
$3\r\nSET\r\n
$11\r\nsession:123\r\n
$9\r\nuser_data\r\n
$2\r\nPX\r\n
$5\r\n60000\r\n
```

#### 만료 처리 전략

키 만료를 처리하는 두 가지 전략이 있습니다:

| 전략 | 설명 | 장점 | 단점 |
|------|------|------|------|
| **Lazy Expiration** | GET 요청 시 만료 확인 | 구현 간단, CPU 효율적 | 메모리에 만료 키 잔존 |
| **Active Expiration** | 주기적으로 만료 키 삭제 | 메모리 효율적 | 추가 스레드/로직 필요 |

> 💡 이 단계에서는 **Lazy Expiration**을 구현합니다. 실제 Redis는 두 전략을 모두 사용합니다.

---

### ✅ 통과 조건

- `SET <key> <value> PX <milliseconds>` 형식으로 만료 시간을 설정할 수 있어야 합니다
- 만료되지 않은 키의 `GET`은 정상적으로 값을 반환해야 합니다
- 만료된 키의 `GET`은 `$-1\r\n`을 반환해야 합니다
- PX 옵션 없이 `SET`한 키는 만료되지 않아야 합니다

---

### 💡 힌트

값과 함께 만료 시간을 저장하는 구조가 필요합니다.

```kotlin
import java.util.concurrent.ConcurrentHashMap

data class Entry(
    val value: String,
    val expiresAt: Long?  // null이면 만료 없음, 아니면 Unix timestamp (ms)
)

val dataStore = ConcurrentHashMap<String, Entry>()

fun handleCommand(command: Command, writer: java.io.BufferedWriter) {
    when (command.name) {
        "SET" -> {
            if (command.args.size < 2) {
                writer.write("-ERR wrong number of arguments for 'set' command\r\n")
                writer.flush()
                return
            }
            
            val key = command.args[0]
            val value = command.args[1]
            var expiresAt: Long? = null
            
            // PX 옵션 파싱
            val pxIndex = command.args.indexOfFirst { it.uppercase() == "PX" }
            if (pxIndex != -1 && pxIndex + 1 < command.args.size) {
                val milliseconds = command.args[pxIndex + 1].toLongOrNull()
                if (milliseconds != null) {
                    expiresAt = System.currentTimeMillis() + milliseconds
                }
            }
            
            dataStore[key] = Entry(value, expiresAt)
            writer.write("+OK\r\n")
        }
        
        "GET" -> {
            if (command.args.isEmpty()) {
                writer.write("-ERR wrong number of arguments for 'get' command\r\n")
                writer.flush()
                return
            }
            
            val key = command.args[0]
            val entry = dataStore[key]
            
            when {
                entry == null -> {
                    writer.write("\$-1\r\n")
                }
                entry.expiresAt != null && System.currentTimeMillis() > entry.expiresAt -> {
                    // Lazy expiration: 만료된 키 삭제
                    dataStore.remove(key)
                    writer.write("\$-1\r\n")
                }
                else -> {
                    writer.write("\$${entry.value.length}\r\n${entry.value}\r\n")
                }
            }
        }
        
        // ... 다른 명령어들
    }
    writer.flush()
}
```

---

### 🧪 테스트 방법

```bash
# 만료 시간 설정 테스트 (100ms)
redis-cli SET mykey "Hello" PX 100
# 예상 출력: OK

# 즉시 조회 (만료 전)
redis-cli GET mykey
# 예상 출력: "Hello"

# 100ms 이상 대기 후 조회
sleep 0.2
redis-cli GET mykey
# 예상 출력: (nil)

# 만료 시간 없이 저장
redis-cli SET permanent "I live forever"
# 예상 출력: OK

# 오래 기다려도 조회 가능
sleep 2
redis-cli GET permanent
# 예상 출력: "I live forever"
```

**좀 더 긴 만료 시간 테스트:**
```bash
# 5초 후 만료
redis-cli SET session "user123" PX 5000
redis-cli GET session  # "user123"
sleep 6
redis-cli GET session  # (nil)
```

---

### 🤔 생각해볼 점

1. **Active Expiration 구현**: 백그라운드 스레드가 주기적으로 만료 키를 삭제하려면 어떻게 해야 할까요?

2. **메모리 누수**: Lazy Expiration만 사용하면 접근하지 않는 만료 키가 메모리에 남습니다. 이를 해결하려면?

3. **추가 옵션 구현**: Redis의 `SET` 명령어는 다양한 옵션을 지원합니다:
   - `EX seconds`: 초 단위 만료
   - `NX`: 키가 없을 때만 설정
   - `XX`: 키가 있을 때만 설정
   
   이 옵션들을 어떻게 추가할 수 있을까요?

---

### 🎉 축하합니다!

모든 Stage를 완료하셨습니다! 이제 여러분은 다음을 구현한 미니 Redis를 가지게 되었습니다:

- ✅ TCP 서버
- ✅ RESP 프로토콜 파싱
- ✅ 동시 클라이언트 처리
- ✅ PING, ECHO, SET, GET 명령어
- ✅ 키 만료 (TTL)

---