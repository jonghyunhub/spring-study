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
| **8** | **트랜잭션 실행** | ⬤⬤⬤ 어려움 |
| 9 | DISCARD 명령어 | ⬤○○ 쉬움 |
| 10 | 트랜잭션 내 실패 처리 | ⬤⬤○ 보통 |
| 11 | 다중 트랜잭션 | ⬤⬤○ 보통 |

---

## Stage 8: 트랜잭션 실행

### 🎯 목표

`EXEC`가 호출되면 **큐에 쌓인 모든 명령어를 순서대로 실행**하고, 각 명령어의 결과를 배열로 반환합니다. 트랜잭션의 핵심 기능입니다!

---

### 📚 배경 지식

#### 트랜잭션 실행 흐름

```bash
MULTI
SET name "Alice"    # QUEUED
SET age "30"        # QUEUED
INCR counter        # QUEUED
GET name            # QUEUED
EXEC
# 응답 (배열):
# 1) OK              <- SET name 결과
# 2) OK              <- SET age 결과
# 3) (integer) 1     <- INCR 결과
# 4) "Alice"         <- GET 결과
```

#### RESP 응답 형식

EXEC는 **배열**을 반환하며, 각 요소는 해당 명령어의 결과입니다:

```
*4\r\n              # 4개의 결과
+OK\r\n             # SET name 결과
+OK\r\n             # SET age 결과
:1\r\n              # INCR 결과
$5\r\nAlice\r\n     # GET 결과
```

#### 원자적 실행

트랜잭션 내 명령어들은 **중간에 다른 클라이언트의 명령어가 끼어들지 않고** 연속으로 실행됩니다:

```
클라이언트 A (트랜잭션)         클라이언트 B
MULTI
SET x 1                         
SET y 2                         
EXEC ─────────────────────────> SET x 100 (대기)
  │ SET x 1 실행                    │
  │ SET y 2 실행                    │
  └─ 완료 ─────────────────────────>│
                                SET x 100 실행
```

---

### ✅ 통과 조건

- EXEC 시 큐에 쌓인 명령어들이 순서대로 실행되어야 합니다
- 각 명령어의 결과가 배열로 반환되어야 합니다
- 실행 후 트랜잭션 모드가 해제되어야 합니다
- 다양한 명령어 타입(SET, GET, INCR 등)이 올바르게 처리되어야 합니다

---

### 💡 힌트

명령어 실행 결과를 수집한 후 배열로 반환합니다.

```kotlin
"EXEC" -> {
    if (!state.inTransaction) {
        writer.write("-ERR EXEC without MULTI\r\n")
        writer.flush()
        return
    }
    
    val commands = state.queuedCommands.toList()
    
    // 트랜잭션 상태 초기화
    state.inTransaction = false
    state.queuedCommands.clear()
    
    // 빈 트랜잭션
    if (commands.isEmpty()) {
        writer.write("*0\r\n")
        writer.flush()
        return
    }
    
    // 결과를 수집할 StringWriter
    val results = mutableListOf<String>()
    
    for (cmd in commands) {
        val resultWriter = java.io.StringWriter()
        val bufferedResultWriter = java.io.BufferedWriter(resultWriter)
        
        // 각 명령어 실행 (트랜잭션 모드가 아닌 상태로)
        executeCommand(cmd, bufferedResultWriter)
        bufferedResultWriter.flush()
        
        results.add(resultWriter.toString())
    }
    
    // 배열 응답 작성
    writer.write("*${results.size}\r\n")
    for (result in results) {
        writer.write(result)
    }
    writer.flush()
}

// 단일 명령어 실행 (결과를 writer에 씀)
fun executeCommand(command: Command, writer: BufferedWriter) {
    when (command.name) {
        "SET" -> handleSet(command, writer)
        "GET" -> handleGet(command, writer)
        "INCR" -> handleIncr(command, writer)
        "RPUSH" -> handleRpush(command, writer)
        "LPUSH" -> handleLpush(command, writer)
        "LRANGE" -> handleLrange(command, writer)
        // ... 다른 명령어들
        else -> {
            writer.write("-ERR unknown command '${command.name}'\r\n")
        }
    }
}
```

**더 깔끔한 구현:**

```kotlin
// 명령어 핸들러를 반환값을 가진 함수로 리팩토링
fun executeCommandForResult(command: Command): String {
    return when (command.name) {
        "SET" -> {
            // SET 로직...
            "+OK\r\n"
        }
        "GET" -> {
            val value = stringStore[command.args[0]]?.value
            if (value == null) "\$-1\r\n"
            else "\$${value.length}\r\n$value\r\n"
        }
        "INCR" -> {
            // INCR 로직...
            ":$newValue\r\n"
        }
        else -> "-ERR unknown command '${command.name}'\r\n"
    }
}

"EXEC" -> {
    // ...
    
    val results = commands.map { executeCommandForResult(it) }
    
    writer.write("*${results.size}\r\n")
    results.forEach { writer.write(it) }
    writer.flush()
}
```

---

### 🧪 테스트 방법

```bash
redis-cli

# 기본 트랜잭션
127.0.0.1:6379> MULTI
OK
127.0.0.1:6379(TX)> SET user:name "Alice"
QUEUED
127.0.0.1:6379(TX)> SET user:age "25"
QUEUED
127.0.0.1:6379(TX)> INCR visitor_count
QUEUED
127.0.0.1:6379(TX)> GET user:name
QUEUED
127.0.0.1:6379(TX)> EXEC
1) OK
2) OK
3) (integer) 1
4) "Alice"

# 값 확인
127.0.0.1:6379> GET user:name
"Alice"
127.0.0.1:6379> GET user:age
"25"
127.0.0.1:6379> GET visitor_count
"1"

# 복잡한 트랜잭션
127.0.0.1:6379> MULTI
OK
127.0.0.1:6379(TX)> RPUSH mylist "a"
QUEUED
127.0.0.1:6379(TX)> RPUSH mylist "b"
QUEUED
127.0.0.1:6379(TX)> RPUSH mylist "c"
QUEUED
127.0.0.1:6379(TX)> LRANGE mylist 0 -1
QUEUED
127.0.0.1:6379(TX)> EXEC
1) (integer) 1
2) (integer) 2
3) (integer) 3
4) 1) "a"
   2) "b"
   3) "c"
```

---

### 🤔 생각해볼 점

1. **격리성(Isolation)**: Redis 트랜잭션은 다른 DB의 트랜잭션과 어떻게 다를까요? 롤백이 가능할까요?

2. **중첩 배열**: LRANGE의 결과가 트랜잭션 결과 배열 안에 들어가면 중첩 배열이 됩니다. RESP에서 어떻게 표현될까요?

3. **성능**: 트랜잭션으로 100개의 명령어를 실행하는 것과 개별로 100번 실행하는 것의 차이는?

---

### ➡️ 다음 단계

Stage 9에서는 **DISCARD** 명령어를 구현하여 트랜잭션을 취소하는 기능을 추가합니다.
