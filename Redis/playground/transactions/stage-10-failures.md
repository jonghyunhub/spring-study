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
| **10** | **트랜잭션 내 실패 처리** | ⬤⬤○ 보통 |
| 11 | 다중 트랜잭션 | ⬤⬤○ 보통 |

---

## Stage 10: 트랜잭션 내 실패 처리

### 🎯 목표

트랜잭션 내에서 **일부 명령어가 실패**해도 나머지 명령어는 정상적으로 실행되도록 합니다. Redis 트랜잭션은 롤백을 지원하지 않습니다!

---

### 📚 배경 지식

#### Redis 트랜잭션의 에러 처리

Redis 트랜잭션은 **롤백(rollback)을 지원하지 않습니다**. 일부 명령어가 실패해도 다른 명령어는 실행됩니다.

```bash
SET foo "hello"
MULTI
INCR foo        # 문자열에 INCR -> 런타임 에러
SET bar "world" # 이건 성공해야 함
EXEC
# 1) (error) ERR value is not an integer or out of range
# 2) OK
```

#### 두 가지 에러 유형

**1. 큐잉 시 에러 (구문 에러)**
```bash
MULTI
SET foo         # 인자 부족
# (error) ERR wrong number of arguments for 'set' command
SET bar "ok"
# QUEUED
EXEC
# (error) EXECABORT Transaction discarded because of previous errors.
```
→ 트랜잭션 전체가 취소됨

**2. 실행 시 에러 (런타임 에러)**
```bash
SET foo "hello"
MULTI
INCR foo        # QUEUED (구문은 올바름)
SET bar "world" # QUEUED
EXEC
# 1) (error) ERR value is not an integer or out of range
# 2) OK           <-- 실행됨!
```
→ 에러가 난 명령어만 실패, 나머지는 실행됨

#### 왜 롤백이 없을까?

Redis는 성능을 위해 롤백을 지원하지 않습니다:
- 롤백 로그를 유지할 필요 없음
- 단순한 구현
- 프로그래밍 에러는 개발 단계에서 잡아야 함

---

### ✅ 통과 조건

- 런타임 에러가 발생한 명령어는 에러를 반환해야 합니다
- 에러가 발생해도 다른 명령어는 정상적으로 실행되어야 합니다
- 결과 배열에 에러와 성공 결과가 함께 포함되어야 합니다

---

### 💡 힌트

각 명령어를 독립적으로 실행하고 결과(또는 에러)를 수집합니다.

```kotlin
"EXEC" -> {
    if (!state.inTransaction) {
        writer.write("-ERR EXEC without MULTI\r\n")
        writer.flush()
        return
    }
    
    val commands = state.queuedCommands.toList()
    state.inTransaction = false
    state.queuedCommands.clear()
    
    if (commands.isEmpty()) {
        writer.write("*0\r\n")
        writer.flush()
        return
    }
    
    // 각 명령어 실행 (에러가 나도 계속 진행)
    val results = commands.map { cmd ->
        try {
            executeCommandForResult(cmd)
        } catch (e: Exception) {
            // 예상치 못한 에러도 처리
            "-ERR ${e.message}\r\n"
        }
    }
    
    // 결과 배열 반환 (에러 포함)
    writer.write("*${results.size}\r\n")
    results.forEach { writer.write(it) }
    writer.flush()
}

fun executeCommandForResult(command: Command): String {
    return when (command.name) {
        "INCR" -> {
            val key = command.args.getOrNull(0)
                ?: return "-ERR wrong number of arguments for 'incr' command\r\n"
            
            // 타입 체크
            if (listStore.containsKey(key)) {
                return "-WRONGTYPE Operation against a key holding the wrong kind of value\r\n"
            }
            
            val entry = stringStore[key]
            val currentValue = entry?.value ?: "0"
            
            val number = currentValue.toLongOrNull()
                ?: return "-ERR value is not an integer or out of range\r\n"
            
            if (number == Long.MAX_VALUE) {
                return "-ERR value is not an integer or out of range\r\n"
            }
            
            val newValue = number + 1
            stringStore[key] = StringEntry(newValue.toString(), entry?.expiresAt)
            ":$newValue\r\n"
        }
        // ... 다른 명령어들
    }
}
```

---

### 🧪 테스트 방법

```bash
redis-cli

# 런타임 에러 테스트
127.0.0.1:6379> SET mystring "hello"
OK

127.0.0.1:6379> MULTI
OK

127.0.0.1:6379(TX)> INCR mystring
QUEUED

127.0.0.1:6379(TX)> SET newkey "value"
QUEUED

127.0.0.1:6379(TX)> GET newkey
QUEUED

127.0.0.1:6379(TX)> EXEC
1) (error) ERR value is not an integer or out of range
2) OK
3) "value"

# newkey가 설정되었는지 확인 (에러에도 불구하고!)
127.0.0.1:6379> GET newkey
"value"

# WRONGTYPE 에러 테스트
127.0.0.1:6379> RPUSH mylist "item"
(integer) 1

127.0.0.1:6379> MULTI
OK

127.0.0.1:6379(TX)> SET beforekey "before"
QUEUED

127.0.0.1:6379(TX)> INCR mylist
QUEUED

127.0.0.1:6379(TX)> SET afterkey "after"
QUEUED

127.0.0.1:6379(TX)> EXEC
1) OK
2) (error) WRONGTYPE Operation against a key holding the wrong kind of value
3) OK

# 에러 앞뒤 명령어 모두 실행됨
127.0.0.1:6379> GET beforekey
"before"
127.0.0.1:6379> GET afterkey
"after"
```

---

### 🤔 생각해볼 점

1. **ACID 속성**: Redis 트랜잭션은 ACID 중 어떤 속성을 보장할까요?
   - **A**(Atomicity): 부분 실패 가능 → X
   - **C**(Consistency): 보장
   - **I**(Isolation): 명령어 사이에 다른 클라이언트 끼어들지 않음 → O
   - **D**(Durability): 설정에 따라 다름

2. **구문 에러 vs 런타임 에러**: 왜 구문 에러는 전체 트랜잭션을 취소하고, 런타임 에러는 그렇지 않을까요?

3. **Lua 스크립트**: 진정한 원자적 실행이 필요하면 Lua 스크립트를 사용합니다. 왜 그럴까요?

---

### ➡️ 다음 단계

Stage 11에서는 **다중 트랜잭션**을 구현합니다. 여러 클라이언트가 동시에 트랜잭션을 실행할 때의 동작을 확인합니다.
