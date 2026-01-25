# Redis Transactions 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | INCR 명령어 (1/3) - 기본 구현 | ⬤○○ 쉬움 |
| 2 | INCR 명령어 (2/3) - 존재하지 않는 키 | ⬤○○ 쉬움 |
| **3** | **INCR 명령어 (3/3) - 에러 처리** | ⬤○○ 쉬움 |
| 4 | MULTI 명령어 | ⬤○○ 쉬움 |
| 5 | EXEC 명령어 | ⬤○○ 쉬움 |
| 6 | 빈 트랜잭션 | ⬤⬤⬤ 어려움 |
| 7 | 명령어 큐잉 | ⬤⬤○ 보통 |
| 8 | 트랜잭션 실행 | ⬤⬤⬤ 어려움 |
| 9 | DISCARD 명령어 | ⬤○○ 쉬움 |
| 10 | 트랜잭션 내 실패 처리 | ⬤⬤○ 보통 |
| 11 | 다중 트랜잭션 | ⬤⬤○ 보통 |

---

## Stage 3: INCR 명령어 (3/3) - 에러 처리

### 🎯 목표

`INCR`의 **에러 케이스**를 처리합니다. 숫자가 아닌 값이나 범위를 벗어난 값에 대해 적절한 에러를 반환해야 합니다.

---

### 📚 배경 지식

#### 에러 케이스

**1. 숫자가 아닌 값**
```bash
SET name "hello"
INCR name
# 에러: ERR value is not an integer or out of range
```

**2. 정수 범위 초과**
```bash
SET bignum "9223372036854775807"   # Long.MAX_VALUE
INCR bignum
# 에러: ERR value is not an integer or out of range
```

**3. 잘못된 타입 (List, Set 등)**
```bash
RPUSH mylist "a"
INCR mylist
# 에러: WRONGTYPE Operation against a key holding the wrong kind of value
```

#### Redis의 정수 범위

Redis의 INCR은 **64비트 부호있는 정수**를 사용합니다:
- 최소값: `-9223372036854775808` (Long.MIN_VALUE)
- 최대값: `9223372036854775807` (Long.MAX_VALUE)

---

### ✅ 통과 조건

- 숫자가 아닌 문자열에 INCR하면 에러를 반환해야 합니다
- 정수 범위를 초과하면 에러를 반환해야 합니다
- List 등 다른 타입의 키에 INCR하면 WRONGTYPE 에러를 반환해야 합니다
- 에러가 발생해도 기존 값은 변경되지 않아야 합니다

---

### 💡 힌트

```kotlin
"INCR" -> {
    if (command.args.isEmpty()) {
        writer.write("-ERR wrong number of arguments for 'incr' command\r\n")
        writer.flush()
        return
    }
    
    val key = command.args[0]
    
    // 1. 타입 충돌 체크 (List 등)
    if (listStore.containsKey(key)) {
        writer.write("-WRONGTYPE Operation against a key holding the wrong kind of value\r\n")
        writer.flush()
        return
    }
    
    // 2. 현재 값 가져오기
    val entry = stringStore[key]
    val currentValue = entry?.value ?: "0"
    
    // 3. 숫자 변환 시도
    val number = try {
        currentValue.toLong()
    } catch (e: NumberFormatException) {
        writer.write("-ERR value is not an integer or out of range\r\n")
        writer.flush()
        return
    }
    
    // 4. 오버플로우 체크
    if (number == Long.MAX_VALUE) {
        writer.write("-ERR value is not an integer or out of range\r\n")
        writer.flush()
        return
    }
    
    // 5. 증가 및 저장
    val newValue = number + 1
    stringStore[key] = StringEntry(newValue.toString(), entry?.expiresAt)
    
    writer.write(":$newValue\r\n")
}
```

---

### 🧪 테스트 방법

```bash
# 숫자가 아닌 값
redis-cli SET greeting "hello"
# 예상 출력: OK

redis-cli INCR greeting
# 예상 출력: (error) ERR value is not an integer or out of range

# 값이 변경되지 않았는지 확인
redis-cli GET greeting
# 예상 출력: "hello"

# 소수점 값 (정수가 아님)
redis-cli SET decimal "3.14"
# 예상 출력: OK

redis-cli INCR decimal
# 예상 출력: (error) ERR value is not an integer or out of range

# 빈 문자열
redis-cli SET empty ""
# 예상 출력: OK

redis-cli INCR empty
# 예상 출력: (error) ERR value is not an integer or out of range

# 오버플로우 테스트
redis-cli SET maxval "9223372036854775807"
# 예상 출력: OK

redis-cli INCR maxval
# 예상 출력: (error) ERR value is not an integer or out of range

# List 타입에 INCR
redis-cli RPUSH mylist "item"
# 예상 출력: (integer) 1

redis-cli INCR mylist
# 예상 출력: (error) WRONGTYPE Operation against a key holding the wrong kind of value
```

---

### 🤔 생각해볼 점

1. **DECR의 언더플로우**: `DECR`에서 `Long.MIN_VALUE`보다 작아지려고 하면 어떻게 해야 할까요?

2. **원자성**: 에러가 발생했을 때 기존 값이 변경되지 않는 것이 왜 중요할까요?

3. **INCR vs INCRBY**: `INCRBY key -5`와 `DECRBY key 5`는 같은 결과를 낼까요?

---

### ➡️ 다음 단계

Stage 4에서는 **MULTI** 명령어를 구현하여 트랜잭션을 시작하는 방법을 배웁니다.
