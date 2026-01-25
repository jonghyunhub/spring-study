# Redis Transactions 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | INCR 명령어 (1/3) - 기본 구현 | ⬤○○ 쉬움 |
| 2 | INCR 명령어 (2/3) - 존재하지 않는 키 | ⬤○○ 쉬움 |
| 3 | INCR 명령어 (3/3) - 에러 처리 | ⬤○○ 쉬움 |
| 4 | MULTI 명령어 | ⬤○○ 쉬움 |
| **5** | **EXEC 명령어** | ⬤○○ 쉬움 |
| 6 | 빈 트랜잭션 | ⬤⬤⬤ 어려움 |
| 7 | 명령어 큐잉 | ⬤⬤○ 보통 |
| 8 | 트랜잭션 실행 | ⬤⬤⬤ 어려움 |
| 9 | DISCARD 명령어 | ⬤○○ 쉬움 |
| 10 | 트랜잭션 내 실패 처리 | ⬤⬤○ 보통 |
| 11 | 다중 트랜잭션 | ⬤⬤○ 보통 |

---

## Stage 5: EXEC 명령어

### 🎯 목표

`EXEC` 명령어를 구현합니다. EXEC는 **트랜잭션을 종료하고 큐에 쌓인 명령어들을 실행**합니다. 이번 단계에서는 기본적인 EXEC 동작만 구현합니다.

---

### 📚 배경 지식

#### EXEC 명령어

```
EXEC
```

트랜잭션 모드를 종료하고 큐에 쌓인 모든 명령어를 순서대로 실행합니다. 각 명령어의 결과를 **배열로 반환**합니다.

```bash
MULTI
SET foo "bar"
GET foo
INCR counter
EXEC
# 응답:
# 1) OK
# 2) "bar"
# 3) (integer) 1
```

#### EXEC without MULTI

트랜잭션 모드가 아닌데 EXEC를 호출하면 에러:

```bash
EXEC
# (error) ERR EXEC without MULTI
```

#### 응답 형식

EXEC의 응답은 **RESP Array**로, 각 명령어의 결과를 담고 있습니다:

```
*3\r\n           # 3개의 결과
+OK\r\n          # SET의 결과
$3\r\nbar\r\n    # GET의 결과
:1\r\n           # INCR의 결과
```

---

### ✅ 통과 조건

- 트랜잭션 모드가 아닐 때 `EXEC`를 호출하면 에러를 반환해야 합니다
- `EXEC` 후 트랜잭션 모드가 해제되어야 합니다
- 이 단계에서는 빈 트랜잭션(`MULTI` 직후 `EXEC`)의 동작만 확인합니다

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
    
    // 트랜잭션 상태 초기화
    state.inTransaction = false
    state.queuedCommands.clear()
    
    // 큐가 비어있으면 빈 배열 반환
    if (commands.isEmpty()) {
        writer.write("*0\r\n")
        writer.flush()
        return
    }
    
    // 명령어 실행 (다음 단계에서 구현)
    // ...
}
```

---

### 🧪 테스트 방법

```bash
# MULTI 없이 EXEC
redis-cli EXEC
# 예상 출력: (error) ERR EXEC without MULTI

# redis-cli 대화형 모드에서 테스트
redis-cli
127.0.0.1:6379> MULTI
OK
127.0.0.1:6379> EXEC
(empty array)

# EXEC 후 트랜잭션 모드 해제 확인
127.0.0.1:6379> EXEC
(error) ERR EXEC without MULTI
```

---

### ➡️ 다음 단계

Stage 6에서는 **빈 트랜잭션**의 동작을 완성합니다.
