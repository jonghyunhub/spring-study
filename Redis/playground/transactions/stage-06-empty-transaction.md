# Redis Transactions 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | INCR 명령어 (1/3) - 기본 구현 | ⬤○○ 쉬움 |
| 2 | INCR 명령어 (2/3) - 존재하지 않는 키 | ⬤○○ 쉬움 |
| 3 | INCR 명령어 (3/3) - 에러 처리 | ⬤○○ 쉬움 |
| 4 | MULTI 명령어 | ⬤○○ 쉬움 |
| 5 | EXEC 명령어 | ⬤○○ 쉬움 |
| **6** | **빈 트랜잭션** | ⬤⬤⬤ 어려움 |
| 7 | 명령어 큐잉 | ⬤⬤○ 보통 |
| 8 | 트랜잭션 실행 | ⬤⬤⬤ 어려움 |
| 9 | DISCARD 명령어 | ⬤○○ 쉬움 |
| 10 | 트랜잭션 내 실패 처리 | ⬤⬤○ 보통 |
| 11 | 다중 트랜잭션 | ⬤⬤○ 보통 |

---

## Stage 6: 빈 트랜잭션

### 🎯 목표

**빈 트랜잭션**의 동작을 완벽하게 구현합니다. MULTI 직후 EXEC를 호출하면 빈 배열을 반환하고, 트랜잭션 상태가 올바르게 정리되어야 합니다.

---

### 📚 배경 지식

#### 빈 트랜잭션

```bash
MULTI
EXEC
# 응답: (empty array)
```

명령어 없이 트랜잭션을 시작하고 종료하면 **빈 배열**을 반환합니다. 이는 에러가 아닙니다.

#### RESP 빈 배열

```
*0\r\n
```

#### 상태 전이

```
일반 모드 ──MULTI──> 트랜잭션 모드 ──EXEC──> 일반 모드
                         │                    │
                         │                    └─> 빈 배열 반환
                         │
                    큐가 비어있음
```

#### 여러 번 반복 가능

빈 트랜잭션 후에도 정상적으로 새 트랜잭션을 시작할 수 있어야 합니다:

```bash
MULTI
EXEC
# (empty array)

MULTI
EXEC
# (empty array)

MULTI
SET foo "bar"
EXEC
# 1) OK
```

---

### ✅ 통과 조건

- `MULTI` 직후 `EXEC`를 호출하면 빈 배열(`*0\r\n`)을 반환해야 합니다
- EXEC 후 트랜잭션 모드가 해제되어야 합니다
- 빈 트랜잭션 후 새로운 트랜잭션을 시작할 수 있어야 합니다
- 빈 트랜잭션 후 일반 명령어도 정상 동작해야 합니다

---

### 💡 힌트

```kotlin
"EXEC" -> {
    if (!state.inTransaction) {
        writer.write("-ERR EXEC without MULTI\r\n")
        writer.flush()
        return
    }
    
    val commands = state.queuedCommands.toList()
    
    // 중요: 먼저 트랜잭션 상태 초기화
    state.inTransaction = false
    state.queuedCommands.clear()
    
    // 빈 트랜잭션이면 빈 배열 반환
    if (commands.isEmpty()) {
        writer.write("*0\r\n")
        writer.flush()
        return
    }
    
    // 명령어가 있으면 실행 (다음 단계에서 구현)
    executeTransaction(commands, writer)
}
```

**중요 포인트:**
1. EXEC 처리 시작 시 즉시 트랜잭션 상태를 초기화해야 합니다
2. 이렇게 해야 명령어 실행 중 에러가 발생해도 트랜잭션이 종료됩니다

---

### 🧪 테스트 방법

```bash
# redis-cli 대화형 모드에서 테스트
redis-cli

# 빈 트랜잭션
127.0.0.1:6379> MULTI
OK
127.0.0.1:6379> EXEC
(empty array)

# 또 빈 트랜잭션
127.0.0.1:6379> MULTI
OK
127.0.0.1:6379> EXEC
(empty array)

# 일반 명령어 동작 확인
127.0.0.1:6379> SET test "value"
OK
127.0.0.1:6379> GET test
"value"

# EXEC 없이 다시 MULTI 가능한지 확인
127.0.0.1:6379> MULTI
OK
127.0.0.1:6379> EXEC
(empty array)
```

---

### 🤔 생각해볼 점

1. **왜 빈 트랜잭션을 허용할까요?** 
   - 조건부 트랜잭션 로직에서 유용할 수 있습니다
   - WATCH와 함께 사용할 때 의미가 있습니다

2. **상태 초기화 타이밍**: 왜 명령어 실행 전에 트랜잭션 상태를 초기화해야 할까요?

---

### ➡️ 다음 단계

Stage 7에서는 **명령어 큐잉**을 구현합니다. 트랜잭션 모드에서 명령어가 즉시 실행되지 않고 큐에 쌓이도록 합니다.
