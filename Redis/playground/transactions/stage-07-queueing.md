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
| **7** | **명령어 큐잉** | ⬤⬤○ 보통 |
| 8 | 트랜잭션 실행 | ⬤⬤⬤ 어려움 |
| 9 | DISCARD 명령어 | ⬤○○ 쉬움 |
| 10 | 트랜잭션 내 실패 처리 | ⬤⬤○ 보통 |
| 11 | 다중 트랜잭션 | ⬤⬤○ 보통 |

---

## Stage 7: 명령어 큐잉

### 🎯 목표

트랜잭션 모드에서 **명령어가 즉시 실행되지 않고 큐에 쌓이도록** 합니다. 명령어가 큐에 추가되면 `QUEUED`를 응답합니다.

---

### 📚 배경 지식

#### QUEUED 응답

트랜잭션 모드에서 명령어를 입력하면 즉시 실행되지 않고 `QUEUED`가 반환됩니다:

```bash
MULTI
# OK
SET foo "bar"
# QUEUED          <-- 실행되지 않고 큐에 추가됨
GET foo
# QUEUED
INCR counter
# QUEUED
EXEC
# 1) OK           <-- 이제 실행됨
# 2) "bar"
# 3) (integer) 1
```

#### RESP QUEUED 응답

```
+QUEUED\r\n
```

Simple String 형식입니다.

#### 예외: EXEC, DISCARD, MULTI

트랜잭션 모드에서도 **일부 명령어는 즉시 처리**됩니다:
- `EXEC`: 트랜잭션 실행
- `DISCARD`: 트랜잭션 취소
- `MULTI`: 에러 반환 (중첩 불가)

```bash
MULTI
# OK
SET foo "bar"
# QUEUED
MULTI           # 이건 큐에 안 들어가고 바로 에러
# (error) ERR MULTI calls can not be nested
```

---

### ✅ 통과 조건

- 트랜잭션 모드에서 일반 명령어를 입력하면 `+QUEUED\r\n`를 반환해야 합니다
- 명령어가 실제로 큐에 저장되어야 합니다
- EXEC, DISCARD, MULTI는 큐에 쌓이지 않고 즉시 처리되어야 합니다

---

### 💡 힌트

명령어 처리 로직에서 트랜잭션 모드를 먼저 확인합니다.

```kotlin
fun handleCommand(command: Command, writer: BufferedWriter, state: TransactionState) {
    // 트랜잭션 모드에서의 특별 처리
    if (state.inTransaction) {
        when (command.name) {
            "EXEC" -> {
                // EXEC 처리 (즉시 실행)
                executeExec(state, writer)
                return
            }
            "DISCARD" -> {
                // DISCARD 처리 (즉시 실행)
                executeDiscard(state, writer)
                return
            }
            "MULTI" -> {
                // 중첩 MULTI 에러 (즉시 처리)
                writer.write("-ERR MULTI calls can not be nested\r\n")
                writer.flush()
                return
            }
            else -> {
                // 그 외 명령어는 큐에 추가
                state.queuedCommands.add(command)
                writer.write("+QUEUED\r\n")
                writer.flush()
                return
            }
        }
    }
    
    // 일반 모드에서의 명령어 처리
    when (command.name) {
        "MULTI" -> {
            state.inTransaction = true
            state.queuedCommands.clear()
            writer.write("+OK\r\n")
        }
        "EXEC" -> {
            writer.write("-ERR EXEC without MULTI\r\n")
        }
        "DISCARD" -> {
            writer.write("-ERR DISCARD without MULTI\r\n")
        }
        "SET" -> {
            // 일반 SET 처리
            handleSet(command, writer)
        }
        // ... 다른 명령어들
    }
    writer.flush()
}
```

---

### 🧪 테스트 방법

```bash
# redis-cli 대화형 모드에서 테스트
redis-cli

127.0.0.1:6379> MULTI
OK

# 명령어들이 QUEUED 반환
127.0.0.1:6379(TX)> SET foo "bar"
QUEUED

127.0.0.1:6379(TX)> GET foo
QUEUED

127.0.0.1:6379(TX)> INCR counter
QUEUED

127.0.0.1:6379(TX)> RPUSH mylist "item"
QUEUED

# 중첩 MULTI는 에러
127.0.0.1:6379(TX)> MULTI
(error) ERR MULTI calls can not be nested

# 큐에 4개의 명령어가 쌓여있음
127.0.0.1:6379(TX)> EXEC
1) OK
2) "bar"
3) (integer) 1
4) (integer) 1
```

---

### 🤔 생각해볼 점

1. **구문 검사**: 잘못된 명령어도 QUEUED가 될까요? 아니면 즉시 에러가 반환될까요?
   ```bash
   MULTI
   SET foo           # 인자 누락
   # 어떤 응답?
   ```

2. **메모리 제한**: 큐에 명령어가 무한히 쌓이면 어떻게 될까요?

---

### ➡️ 다음 단계

Stage 8에서는 **트랜잭션을 실제로 실행**하여 결과를 반환합니다.
