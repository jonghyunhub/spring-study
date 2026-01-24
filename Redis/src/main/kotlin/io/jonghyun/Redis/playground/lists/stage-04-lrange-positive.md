# Redis Lists 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 리스트 생성하기 | ⬤○○ 쉬움 |
| 2 | 요소 추가하기 (RPUSH) | ⬤○○ 쉬움 |
| 3 | 여러 요소 추가하기 | ⬤○○ 쉬움 |
| **4** | **요소 조회하기 - 양수 인덱스 (LRANGE)** | ⬤○○ 쉬움 |
| 5 | 요소 조회하기 - 음수 인덱스 | ⬤○○ 쉬움 |
| 6 | 앞에 요소 추가하기 (LPUSH) | ⬤○○ 쉬움 |
| 7 | 리스트 길이 조회하기 (LLEN) | ⬤○○ 쉬움 |
| 8 | 요소 제거하기 (LPOP/RPOP) | ⬤○○ 쉬움 |
| 9 | 여러 요소 제거하기 | ⬤○○ 쉬움 |
| 10 | 블로킹 조회 (BLPOP/BRPOP) | ⬤⬤○ 보통 |
| 11 | 타임아웃이 있는 블로킹 조회 | ⬤⬤○ 보통 |

---

## Stage 4: 요소 조회하기 - 양수 인덱스 (LRANGE)

### 🎯 목표

`LRANGE` 명령어를 구현하여 리스트의 요소들을 조회할 수 있도록 합니다. 이번 단계에서는 **양수 인덱스**만 처리합니다.

---

### 📚 배경 지식

#### LRANGE 명령어

```
LRANGE <key> <start> <stop>
```

리스트에서 `start`부터 `stop`까지의 요소들을 반환합니다. 인덱스는 **0부터 시작**하고, `start`와 `stop` **모두 포함(inclusive)** 됩니다.

```bash
RPUSH mylist "a" "b" "c" "d" "e"
# 리스트: ["a", "b", "c", "d", "e"]
#          0    1    2    3    4

LRANGE mylist 0 2    # ["a", "b", "c"]
LRANGE mylist 1 3    # ["b", "c", "d"]
LRANGE mylist 0 0    # ["a"]
```

#### 범위를 벗어난 인덱스

Redis는 범위를 벗어난 인덱스를 **유연하게 처리**합니다:

```bash
# 리스트 길이: 5 (인덱스 0~4)
LRANGE mylist 0 100   # ["a", "b", "c", "d", "e"] - stop이 범위 초과해도 OK
LRANGE mylist 10 20   # [] - 빈 배열 반환
```

#### RESP Array 응답

```
*3\r\n           # 배열 요소 3개
$1\r\na\r\n      # "a"
$1\r\nb\r\n      # "b"
$1\r\nc\r\n      # "c"
```

빈 배열:
```
*0\r\n
```

존재하지 않는 키:
```
*0\r\n           # 빈 배열 반환 (에러 아님)
```

---

### ✅ 통과 조건

- `LRANGE <key> <start> <stop>` 형식으로 요소들을 조회할 수 있어야 합니다
- 응답은 RESP Array 형식이어야 합니다
- `stop`이 리스트 길이를 초과하면 마지막 요소까지만 반환해야 합니다
- 존재하지 않는 키는 빈 배열을 반환해야 합니다
- `start`가 리스트 길이보다 크면 빈 배열을 반환해야 합니다

---

### 💡 힌트

```kotlin
"LRANGE" -> {
    if (command.args.size < 3) {
        writer.write("-ERR wrong number of arguments for 'lrange' command\r\n")
        writer.flush()
        return
    }
    
    val key = command.args[0]
    val start = command.args[1].toIntOrNull() ?: 0
    val stop = command.args[2].toIntOrNull() ?: -1
    
    // 타입 충돌 체크
    if (stringStore.containsKey(key)) {
        writer.write("-WRONGTYPE Operation against a key holding the wrong kind of value\r\n")
        writer.flush()
        return
    }
    
    val list = listStore[key]
    
    // 키가 존재하지 않으면 빈 배열
    if (list == null || list.isEmpty()) {
        writer.write("*0\r\n")
        writer.flush()
        return
    }
    
    // 인덱스 정규화 (이번 단계에서는 양수만)
    val size = list.size
    val actualStart = start.coerceIn(0, size)
    val actualStop = stop.coerceIn(0, size - 1)
    
    // start가 stop보다 크면 빈 배열
    if (actualStart > actualStop) {
        writer.write("*0\r\n")
        writer.flush()
        return
    }
    
    // 범위 내 요소 추출
    val elements = list.subList(actualStart, actualStop + 1)
    
    // RESP Array 형식으로 응답
    writer.write("*${elements.size}\r\n")
    for (element in elements) {
        writer.write("\$${element.length}\r\n$element\r\n")
    }
}
```

---

### 🧪 테스트 방법

```bash
# 리스트 생성
redis-cli RPUSH mylist "a" "b" "c" "d" "e"
# 예상 출력: (integer) 5

# 전체 조회
redis-cli LRANGE mylist 0 4
# 예상 출력:
# 1) "a"
# 2) "b"
# 3) "c"
# 4) "d"
# 5) "e"

# 부분 조회
redis-cli LRANGE mylist 1 3
# 예상 출력:
# 1) "b"
# 2) "c"
# 3) "d"

# 단일 요소 조회
redis-cli LRANGE mylist 2 2
# 예상 출력:
# 1) "c"

# 범위 초과 (유연하게 처리)
redis-cli LRANGE mylist 0 100
# 예상 출력: 전체 리스트

# 존재하지 않는 키
redis-cli LRANGE nonexistent 0 10
# 예상 출력: (empty array)

# start가 범위 초과
redis-cli LRANGE mylist 10 20
# 예상 출력: (empty array)
```

---

### 🤔 생각해볼 점

1. **시간 복잡도**: `LRANGE`의 시간 복잡도는 O(S+N)입니다 (S: start까지의 거리, N: 반환할 요소 수). 왜 그럴까요?

2. **메모리 사용**: 매우 긴 리스트에서 `LRANGE mylist 0 -1`로 전체를 조회하면 어떤 문제가 발생할 수 있을까요?

---

### ➡️ 다음 단계

Stage 5에서는 **음수 인덱스**를 처리하여 리스트 끝에서부터 요소를 참조할 수 있도록 합니다.
