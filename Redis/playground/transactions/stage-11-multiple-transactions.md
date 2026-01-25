# Redis Transactions 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | INCR 명령어 (1/3) - 기본 구현 | ⬤○○ 쉬움 |
| 2 | INCR 명령어 (2/3) - 존재하지 않는 키 | ⬤○○ 쉬움 |
| 3 | INCR 명령어 (3/3) - 에러 처리 | ⬤○○ 쉬움 |
| 4 | MULTI 명령어 | ⬤○○ 쉬움 |
| 5 | EXEC 명령어 | ⬤○○ 쉬움 |
| 6 | 빈 트랜잭션 | ⬤⬤⬤ 어려움 |
| 7 | 명령어 큐잉 | ⬤⬤○ 보통 |
| 8 | 트랜잭션 실행 | ⬤⬤⬤ 어려움 |
| 9 | DISCARD 명령어 | ⬤○○ 쉬움 |
| 10 | 트랜잭션 내 실패 처리 | ⬤⬤○ 보통 |
| **11** | **다중 트랜잭션** | ⬤⬤○ 보통 |

---

## Stage 11: 다중 트랜잭션

### 🎯 목표

**여러 클라이언트가 동시에 트랜잭션을 실행**할 때 각 트랜잭션이 독립적으로 동작하고, EXEC 시점에 **원자적으로 실행**되도록 합니다.

---

### 📚 배경 지식

#### 클라이언트 독립성

각 클라이언트의 트랜잭션은 **독립적**입니다:

```
클라이언트 A                     클라이언트 B
MULTI                            
SET x 1                          MULTI
SET y 2                          SET x 100
                                 SET z 200
EXEC ─────────────────>          
  x=1, y=2 설정됨                 EXEC ─────────────────>
                                   x=100, z=200 설정됨
```

#### 트랜잭션의 격리

EXEC가 호출되면 해당 트랜잭션의 모든 명령어가 **연속으로 실행**됩니다. 중간에 다른 클라이언트의 명령어가 끼어들지 않습니다.

```
시간 →

클라이언트 A: MULTI, SET x 1, SET y 2, EXEC
클라이언트 B: MULTI, SET x 100, GET x, EXEC

실행 순서 가능성 1:
[A의 SET x 1, A의 SET y 2] → [B의 SET x 100, B의 GET x]
결과: x=100, y=2

실행 순서 가능성 2:
[B의 SET x 100, B의 GET x] → [A의 SET x 1, A의 SET y 2]
결과: x=1, y=2
```

#### 동시성 제어

멀티스레드 환경에서 EXEC 실행 시 다른 명령어가 끼어들지 않도록 **락(lock)** 이 필요합니다:

```kotlin
val globalLock = ReentrantLock()

"EXEC" -> {
    globalLock.lock()
    try {
        // 트랜잭션 내 모든 명령어 실행
    } finally {
        globalLock.unlock()
    }
}
```

---

### ✅ 통과 조건

- 여러 클라이언트가 동시에 MULTI를 시작할 수 있어야 합니다
- 각 클라이언트의 큐는 독립적이어야 합니다
- EXEC 시 해당 트랜잭션의 명령어들이 원자적으로 실행되어야 합니다
- 한 클라이언트의 EXEC가 다른 클라이언트의 일반 명령어에 의해 중단되지 않아야 합니다

---

### 💡 힌트

클라이언트별 상태 관리와 실행 시 락이 필요합니다.

```kotlin
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.net.Socket

// 클라이언트별 트랜잭션 상태
data class TransactionState(
    var inTransaction: Boolean = false,
    val queuedCommands: MutableList<Command> = mutableListOf()
)

val clientStates = ConcurrentHashMap<Socket, TransactionState>()

// 전역 실행 락 (트랜잭션 원자성 보장)
val executionLock = ReentrantLock()

fun handleClient(clientSocket: Socket) {
    val state = clientStates.computeIfAbsent(clientSocket) { TransactionState() }
    val reader = clientSocket.getInputStream().bufferedReader()
    val writer = clientSocket.getOutputStream().bufferedWriter()
    
    try {
        while (true) {
            val command = parseCommand(reader) ?: break
            
            // 트랜잭션 모드가 아닌 일반 명령어도 락 필요
            // (트랜잭션 실행 중에 끼어들지 않도록)
            if (!state.inTransaction || command.name in listOf("EXEC", "DISCARD", "MULTI")) {
                executionLock.lock()
                try {
                    handleCommand(command, writer, state)
                } finally {
                    executionLock.unlock()
                }
            } else {
                // QUEUED는 락 없이 처리 (각 클라이언트 독립)
                handleCommand(command, writer, state)
            }
        }
    } finally {
        clientStates.remove(clientSocket)
        clientSocket.close()
    }
}

fun executeExec(state: TransactionState, writer: BufferedWriter) {
    // executionLock은 이미 잡힌 상태
    
    val commands = state.queuedCommands.toList()
    state.inTransaction = false
    state.queuedCommands.clear()
    
    if (commands.isEmpty()) {
        writer.write("*0\r\n")
        return
    }
    
    // 모든 명령어를 연속으로 실행 (중간에 다른 클라이언트 끼어들지 않음)
    val results = commands.map { executeCommandForResult(it) }
    
    writer.write("*${results.size}\r\n")
    results.forEach { writer.write(it) }
}
```

---

### 🧪 테스트 방법

**두 개의 터미널에서 동시에 테스트:**

**터미널 1:**
```bash
redis-cli

127.0.0.1:6379> MULTI
OK
127.0.0.1:6379(TX)> SET counter 0
QUEUED
127.0.0.1:6379(TX)> INCR counter
QUEUED
127.0.0.1:6379(TX)> INCR counter
QUEUED
127.0.0.1:6379(TX)> INCR counter
QUEUED
# 아직 EXEC 하지 않음
```

**터미널 2:**
```bash
redis-cli

127.0.0.1:6379> MULTI
OK
127.0.0.1:6379(TX)> SET counter 100
QUEUED
127.0.0.1:6379(TX)> GET counter
QUEUED
# 아직 EXEC 하지 않음
```

**터미널 1에서 EXEC:**
```bash
127.0.0.1:6379(TX)> EXEC
1) OK
2) (integer) 1
3) (integer) 2
4) (integer) 3
```

**터미널 2에서 EXEC:**
```bash
127.0.0.1:6379(TX)> EXEC
1) OK
2) "100"
```

**결과 확인:**
```bash
127.0.0.1:6379> GET counter
"100"    # 터미널 2의 트랜잭션이 나중에 실행됨
```

**동시 EXEC 테스트 (스크립트):**
```bash
# 두 클라이언트가 동시에 같은 키를 수정
# client1.sh
redis-cli <<EOF
MULTI
SET shared_key "from_client_1"
EXEC
EOF

# client2.sh
redis-cli <<EOF
MULTI
SET shared_key "from_client_2"
EXEC
EOF

# 동시 실행
./client1.sh & ./client2.sh &
wait

# 결과는 둘 중 하나 (마지막에 EXEC된 것)
redis-cli GET shared_key
```

---

### 🤔 생각해볼 점

1. **WATCH**: 낙관적 락(Optimistic Locking)을 위한 WATCH 명령어는 어떻게 구현할까요?
   ```bash
   WATCH mykey
   val = GET mykey
   MULTI
   SET mykey (val + 1)
   EXEC    # mykey가 변경됐으면 nil 반환
   ```

2. **성능 트레이드오프**: 전역 락은 단순하지만 동시성이 떨어집니다. 더 세밀한 락킹 전략은?

3. **Redis의 실제 구현**: Redis는 단일 스레드라서 락이 필요 없습니다. 이것이 트랜잭션 구현을 어떻게 단순화할까요?

---

### 🎉 축하합니다!

모든 Transactions Stage를 완료하셨습니다! 이제 여러분은 다음을 구현한 Redis 트랜잭션을 가지게 되었습니다:

- ✅ INCR (원자적 증가)
- ✅ MULTI (트랜잭션 시작)
- ✅ EXEC (트랜잭션 실행)
- ✅ DISCARD (트랜잭션 취소)
- ✅ 명령어 큐잉 (QUEUED 응답)
- ✅ 런타임 에러 처리 (부분 실패)
- ✅ 다중 클라이언트 지원

---

### 🚀 더 나아가기 (보너스 챌린지)

| 챌린지 | 난이도 | 배울 수 있는 것 |
|--------|--------|-----------------|
| WATCH | ⬤⬤⬤ | 낙관적 락, CAS 패턴 |
| UNWATCH | ⬤○○ | WATCH 해제 |
| 구문 에러 시 EXECABORT | ⬤⬤○ | 큐잉 시 에러 처리 |
| DECR, DECRBY, INCRBY | ⬤○○ | 원자적 연산 확장 |
| Lua 스크립트 (EVAL) | ⬤⬤⬤ | 진정한 원자적 실행 |

---

### 📖 학습 포인트 정리

1. **Redis 트랜잭션은 롤백을 지원하지 않음** - 성능을 위한 설계 결정
2. **격리(Isolation)는 보장됨** - EXEC 내 명령어는 연속 실행
3. **낙관적 락이 필요하면 WATCH 사용** - 키가 변경되면 트랜잭션 자동 취소
4. **진정한 원자성이 필요하면 Lua 스크립트** - 조건부 로직도 원자적 실행 가능
5. **단일 스레드의 장점** - 락 없이 트랜잭션 격리 보장

---

### 📚 다음 학습 주제

Redis의 다른 고급 기능도 구현해보세요:

1. **Pub/Sub**: PUBLISH, SUBSCRIBE, PSUBSCRIBE
2. **Lua 스크립트**: EVAL, EVALSHA
3. **Persistence**: RDB 스냅샷, AOF 로그
4. **Replication**: 마스터-슬레이브 복제
5. **Cluster**: 샤딩, 슬롯 할당
