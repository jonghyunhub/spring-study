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
| **9** | **DISCARD 명령어** | ⬤○○ 쉬움 |
| 10 | 트랜잭션 내 실패 처리 | ⬤⬤○ 보통 |
| 11 | 다중 트랜잭션 | ⬤⬤○ 보통 |

---

## Stage 9: DISCARD 명령어

### 🎯 목표

`DISCARD` 명령어를 구현하여 **트랜잭션을 취소**할 수 있도록 합니다. 큐에 쌓인 모든 명령어를 버리고 일반 모드로 돌아갑니다.

---

### 📚 배경 지식

#### DISCARD 명령어

```
DISCARD
```

트랜잭션을 취소하고 큐에 쌓인 모든 명령어를 삭제합니다.

```bash
MULTI
# OK
SET foo "bar"
# QUEUED
SET baz "qux"
# QUEUED
DISCARD
# OK              <-- 트랜잭션 취소됨

GET foo
# (nil)           <-- SET이 실행되지 않았음
```

#### 응답

```
+OK\r\n
```

#### DISCARD without MULTI

트랜잭션 모드가 아닐 때 DISCARD를 호출하면 에러:

```bash
DISCARD
# (error) ERR DISCARD without MULTI
```

#### 실무 활용

```bash
MULTI
SET balance "100"
# 어떤 조건을 확인...
# 조건이 맞지 않으면 취소
DISCARD
```

---

### ✅ 통과 조건

- `DISCARD` 명령어가 `+OK\r\n`를 반환해야 합니다
- 트랜잭션 모드가 해제되어야 합니다
- 큐에 쌓인 명령어가 모두 삭제되어야 합니다
- 트랜잭션 모드가 아닐 때 DISCARD하면 에러를 반환해야 합니다

---

### 💡 힌트

```kotlin
"DISCARD" -> {
    if (!state.inTransaction) {
        writer.write("-ERR DISCARD without MULTI\r\n")
        writer.flush()
        return
    }
    
    // 트랜잭션 상태 초기화
    state.inTransaction = false
    state.queuedCommands.clear()
    
    writer.write("+OK\r\n")
    writer.flush()
}
```

트랜잭션 모드에서 DISCARD가 호출되면 큐잉하지 않고 즉시 처리해야 합니다:

```kotlin
if (state.inTransaction) {
    when (command.name) {
        "EXEC" -> { /* ... */ }
        "DISCARD" -> {
            state.inTransaction = false
            state.queuedCommands.clear()
            writer.write("+OK\r\n")
            writer.flush()
            return
        }
        "MULTI" -> { /* 에러 */ }
        else -> { /* QUEUED */ }
    }
}
```

---

### 🧪 테스트 방법

```bash
# redis-cli 대화형 모드
redis-cli

# DISCARD without MULTI
127.0.0.1:6379> DISCARD
(error) ERR DISCARD without MULTI

# 정상적인 DISCARD
127.0.0.1:6379> MULTI
OK
127.0.0.1:6379(TX)> SET foo "bar"
QUEUED
127.0.0.1:6379(TX)> SET baz "qux"
QUEUED
127.0.0.1:6379(TX)> INCR counter
QUEUED
127.0.0.1:6379(TX)> DISCARD
OK

# 트랜잭션이 취소되었는지 확인
127.0.0.1:6379> GET foo
(nil)
127.0.0.1:6379> GET baz
(nil)
127.0.0.1:6379> GET counter
(nil)

# 일반 모드로 돌아왔는지 확인
127.0.0.1:6379> SET test "value"
OK
127.0.0.1:6379> GET test
"value"

# 새 트랜잭션 시작 가능
127.0.0.1:6379> MULTI
OK
127.0.0.1:6379(TX)> DISCARD
OK
```

---

### 🤔 생각해볼 점

1. **WATCH와 DISCARD**: WATCH로 키를 감시하다가 DISCARD하면 WATCH도 해제될까요?
   ```bash
   WATCH mykey
   MULTI
   SET mykey "value"
   DISCARD
   # mykey는 여전히 WATCH 중일까?
   ```

2. **에러 복구**: 트랜잭션 중 구문 에러가 발생하면 자동으로 DISCARD 될까요?

---

### ➡️ 다음 단계

Stage 10에서는 **트랜잭션 내 실패 처리**를 구현합니다. 일부 명령어가 실패해도 다른 명령어는 실행되어야 합니다.
