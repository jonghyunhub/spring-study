# Redis Transactions 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | INCR 명령어 (1/3) - 기본 구현 | ⬤○○ 쉬움 |
| 2 | INCR 명령어 (2/3) - 존재하지 않는 키 | ⬤○○ 쉬움 |
| 3 | INCR 명령어 (3/3) - 에러 처리 | ⬤○○ 쉬움 |
| **4** | **MULTI 명령어** | ⬤○○ 쉬움 |
| 5 | EXEC 명령어 | ⬤○○ 쉬움 |
| 6 | 빈 트랜잭션 | ⬤⬤⬤ 어려움 |
| 7 | 명령어 큐잉 | ⬤⬤○ 보통 |
| 8 | 트랜잭션 실행 | ⬤⬤⬤ 어려움 |
| 9 | DISCARD 명령어 | ⬤○○ 쉬움 |
| 10 | 트랜잭션 내 실패 처리 | ⬤⬤○ 보통 |
| 11 | 다중 트랜잭션 | ⬤⬤○ 보통 |

---

## Stage 4: MULTI 명령어

### 🎯 목표

`MULTI` 명령어를 구현하여 **트랜잭션을 시작**할 수 있도록 합니다. MULTI는 이후 명령어들이 즉시 실행되지 않고 큐에 쌓이도록 합니다.

---

### 📚 배경 지식

#### Redis 트랜잭션이란?

Redis 트랜잭션은 **여러 명령어를 하나의 단위로 실행**하는 기능입니다.

```bash
MULTI           # 트랜잭션 시작
SET foo "bar"   # 큐에 추가 (아직 실행 안 됨)
INCR counter    # 큐에 추가
GET foo         # 큐에 추가
EXEC            # 모든 명령어 실행
```

#### MULTI 명령어

```
MULTI
```

트랜잭션 모드를 시작합니다. 이후 명령어들은 즉시 실행되지 않고 **큐에 저장**됩니다.

**응답:** `+OK\r\n`

#### 트랜잭션 상태

```
일반 모드                    트랜잭션 모드
    │                            │
    │  MULTI                     │
    └───────────────────────────>│
                                 │ 명령어들이 큐에 쌓임
                                 │
    │  EXEC                      │
    │<───────────────────────────┘
    │                            
 결과 반환
```

#### 중첩 MULTI는 불가

이미 트랜잭션 모드에서 MULTI를 호출하면 에러:

```bash
MULTI
# OK
MULTI
# (error) ERR MULTI calls can not be nested
```

---

### ✅ 통과 조건

- `MULTI` 명령어가 `+OK\r\n`를 반환해야 합니다
- 클라이언트별로 트랜잭션 상태를 관리해야 합니다
- 이미 트랜잭션 모드에서 MULTI를 호출하면 에러를 반환해야 합니다

---

### 💡 힌트

클라이언트별 트랜잭션 상태를 관리하는 구조가 필요합니다.

```kotlin
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

// 클라이언트별 트랜잭션 상태
data class TransactionState(
    var inTransaction: Boolean = false,
    val queuedCommands: MutableList<Command> = mutableListOf()
)

val clientStates = ConcurrentHashMap<Socket, TransactionState>()

fun handleClient(clientSocket: Socket) {
    // 클라이언트 상태 초기화
    val state = clientStates.computeIfAbsent(clientSocket) { TransactionState() }
    
    val reader = clientSocket.getInputStream().bufferedReader()
    val writer = clientSocket.getOutputStream().bufferedWriter()
    
    try {
        while (true) {
            val command = parseCommand(reader) ?: break
            handleCommand(command, writer, state)
        }
    } finally {
        // 연결 종료 시 상태 정리
        clientStates.remove(clientSocket)
        clientSocket.close()
    }
}

fun handleCommand(command: Command, writer: BufferedWriter, state: TransactionState) {
    when (command.name) {
        "MULTI" -> {
            if (state.inTransaction) {
                writer.write("-ERR MULTI calls can not be nested\r\n")
            } else {
                state.inTransaction = true
                state.queuedCommands.clear()
                writer.write("+OK\r\n")
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
# MULTI 시작
redis-cli MULTI
# 예상 출력: OK

# 새 연결에서 MULTI
redis-cli MULTI
# 예상 출력: OK

# 같은 연결에서 중첩 MULTI (redis-cli 대화형 모드에서 테스트)
redis-cli
127.0.0.1:6379> MULTI
OK
127.0.0.1:6379> MULTI
(error) ERR MULTI calls can not be nested
```

---

### 🤔 생각해볼 점

1. **클라이언트 식별**: 각 클라이언트의 트랜잭션 상태를 어떻게 구분할까요? Socket 객체? 클라이언트 ID?

2. **메모리 관리**: 클라이언트가 MULTI만 호출하고 연결을 끊으면 상태는 어떻게 정리될까요?

---

### ➡️ 다음 단계

Stage 5에서는 **EXEC** 명령어를 구현하여 큐에 쌓인 명령어들을 실행합니다.
