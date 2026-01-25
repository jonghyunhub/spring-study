# Redis Transactions 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | INCR 명령어 (1/3) - 기본 구현 | ⬤○○ 쉬움 |
| **2** | **INCR 명령어 (2/3) - 존재하지 않는 키** | ⬤○○ 쉬움 |
| 3 | INCR 명령어 (3/3) - 에러 처리 | ⬤○○ 쉬움 |
| 4 | MULTI 명령어 | ⬤○○ 쉬움 |
| 5 | EXEC 명령어 | ⬤○○ 쉬움 |
| 6 | 빈 트랜잭션 | ⬤⬤⬤ 어려움 |
| 7 | 명령어 큐잉 | ⬤⬤○ 보통 |
| 8 | 트랜잭션 실행 | ⬤⬤⬤ 어려움 |
| 9 | DISCARD 명령어 | ⬤○○ 쉬움 |
| 10 | 트랜잭션 내 실패 처리 | ⬤⬤○ 보통 |
| 11 | 다중 트랜잭션 | ⬤⬤○ 보통 |

---

## Stage 2: INCR 명령어 (2/3) - 존재하지 않는 키

### 🎯 목표

존재하지 않는 키에 대해 `INCR`을 호출했을 때의 동작을 구현합니다. Redis는 이 경우 키를 **0으로 초기화한 후 증가**시킵니다.

---

### 📚 배경 지식

#### 암묵적 초기화

Redis는 존재하지 않는 키에 INCR을 호출하면 자동으로 0으로 초기화합니다:

```bash
# newkey가 존재하지 않는 상태
GET newkey           # (nil)
INCR newkey          # 응답: 1  (0에서 1로 증가)
GET newkey           # "1"
INCR newkey          # 응답: 2
```

이 동작 덕분에 카운터 초기화를 별도로 할 필요가 없습니다:

```bash
# 이렇게 할 필요 없음
if not EXISTS page:views then
    SET page:views 0
INCR page:views

# 그냥 바로 INCR
INCR page:views   # 첫 호출이면 1, 아니면 +1
```

#### DECR도 마찬가지

```bash
GET nonexistent      # (nil)
DECR nonexistent     # 응답: -1  (0에서 -1로 감소)
```

---

### ✅ 통과 조건

- 존재하지 않는 키에 `INCR`하면 `1`을 반환해야 합니다
- 해당 키가 `"1"` 값으로 생성되어야 합니다
- 이후 `INCR`은 정상적으로 증가해야 합니다

---

### 💡 힌트

키가 없을 때 기본값을 0으로 처리합니다.

```kotlin
"INCR" -> {
    if (command.args.isEmpty()) {
        writer.write("-ERR wrong number of arguments for 'incr' command\r\n")
        writer.flush()
        return
    }
    
    val key = command.args[0]
    
    // 현재 값 가져오기 (없으면 "0")
    val entry = stringStore[key]
    val currentValue = entry?.value ?: "0"  // 핵심: 없으면 0
    
    val number = currentValue.toLongOrNull()
    if (number == null) {
        writer.write("-ERR value is not an integer or out of range\r\n")
        writer.flush()
        return
    }
    
    val newValue = number + 1
    
    // 저장 (새 키면 expiresAt은 null)
    stringStore[key] = StringEntry(newValue.toString(), entry?.expiresAt)
    
    writer.write(":$newValue\r\n")
}
```

---

### 🧪 테스트 방법

```bash
# 존재하지 않는 키 확인
redis-cli GET brandnewkey
# 예상 출력: (nil)

# 새 키에 INCR (0 → 1)
redis-cli INCR brandnewkey
# 예상 출력: (integer) 1

# 값 확인
redis-cli GET brandnewkey
# 예상 출력: "1"

# 계속 증가
redis-cli INCR brandnewkey
# 예상 출력: (integer) 2

redis-cli INCR brandnewkey
# 예상 출력: (integer) 3

# 또 다른 새 키
redis-cli INCR anotherkey
# 예상 출력: (integer) 1
```

---

### 🤔 생각해볼 점

1. **INCRBY**: `INCRBY key 5`처럼 증가량을 지정하는 명령어도 있습니다. 어떻게 구현할 수 있을까요?

2. **INCRBYFLOAT**: 소수점 증가도 가능합니다. `INCRBYFLOAT key 0.5`

---

### ➡️ 다음 단계

Stage 3에서는 **숫자가 아닌 값**에 대한 에러 처리를 구현합니다.
