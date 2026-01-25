# Redis Transactions 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| **1** | **INCR 명령어 (1/3) - 기본 구현** | ⬤○○ 쉬움 |
| 2 | INCR 명령어 (2/3) - 존재하지 않는 키 | ⬤○○ 쉬움 |
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

## Stage 1: INCR 명령어 (1/3) - 기본 구현

### 🎯 목표

`INCR` 명령어를 구현합니다. 이 명령어는 키에 저장된 숫자를 **1 증가**시킵니다. 트랜잭션을 배우기 전에 원자적 연산의 기초를 다집니다.

---

### 📚 배경 지식

#### INCR 명령어

```
INCR <key>
```

키에 저장된 정수 값을 1 증가시키고, 증가된 값을 반환합니다.

```bash
SET counter "10"
INCR counter    # 응답: 11
INCR counter    # 응답: 12
INCR counter    # 응답: 13
```

#### 왜 INCR이 중요한가?

`INCR`은 **원자적(atomic)** 연산입니다. 다음 두 코드의 차이를 보세요:

```bash
# 비원자적 (문제 발생 가능!)
val = GET counter
val = val + 1
SET counter val

# 원자적 (안전!)
INCR counter
```

여러 클라이언트가 동시에 접근해도 `INCR`은 값이 정확히 1씩 증가합니다.

#### 실무 활용

```bash
# 페이지 조회수
INCR page:home:views

# 좋아요 수
INCR post:123:likes

# Rate Limiting (1분당 요청 수)
INCR user:456:requests:202401241530
EXPIRE user:456:requests:202401241530 60
```

---

### ✅ 통과 조건

- `INCR <key>` 명령어로 숫자를 1 증가시킬 수 있어야 합니다
- 응답은 RESP Integer 형식(`:N\r\n`)이어야 합니다
- 증가된 값을 반환해야 합니다

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
    
    // 현재 값 가져오기
    val entry = stringStore[key]
    val currentValue = entry?.value ?: "0"
    
    // 숫자로 변환
    val number = currentValue.toLongOrNull()
    if (number == null) {
        writer.write("-ERR value is not an integer or out of range\r\n")
        writer.flush()
        return
    }
    
    // 1 증가
    val newValue = number + 1
    
    // 저장 (기존 TTL 유지)
    stringStore[key] = StringEntry(newValue.toString(), entry?.expiresAt)
    
    // Integer 응답
    writer.write(":$newValue\r\n")
}
```

---

### 🧪 테스트 방법

```bash
# 초기값 설정
redis-cli SET mycounter "10"
# 예상 출력: OK

# INCR 테스트
redis-cli INCR mycounter
# 예상 출력: (integer) 11

redis-cli INCR mycounter
# 예상 출력: (integer) 12

redis-cli INCR mycounter
# 예상 출력: (integer) 13

# 현재 값 확인
redis-cli GET mycounter
# 예상 출력: "13"
```

---

### ➡️ 다음 단계

Stage 2에서는 **존재하지 않는 키**에 대한 INCR 처리를 구현합니다.
