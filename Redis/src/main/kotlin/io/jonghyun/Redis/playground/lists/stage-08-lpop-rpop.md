# Redis Lists 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 리스트 생성하기 | ⬤○○ 쉬움 |
| 2 | 요소 추가하기 (RPUSH) | ⬤○○ 쉬움 |
| 3 | 여러 요소 추가하기 | ⬤○○ 쉬움 |
| 4 | 요소 조회하기 - 양수 인덱스 (LRANGE) | ⬤○○ 쉬움 |
| 5 | 요소 조회하기 - 음수 인덱스 | ⬤○○ 쉬움 |
| 6 | 앞에 요소 추가하기 (LPUSH) | ⬤○○ 쉬움 |
| 7 | 리스트 길이 조회하기 (LLEN) | ⬤○○ 쉬움 |
| **8** | **요소 제거하기 (LPOP/RPOP)** | ⬤○○ 쉬움 |
| 9 | 여러 요소 제거하기 | ⬤○○ 쉬움 |
| 10 | 블로킹 조회 (BLPOP/BRPOP) | ⬤⬤○ 보통 |
| 11 | 타임아웃이 있는 블로킹 조회 | ⬤⬤○ 보통 |

---

## Stage 8: 요소 제거하기 (LPOP/RPOP)

### 🎯 목표

`LPOP`과 `RPOP` 명령어를 구현하여 리스트에서 **요소를 제거하고 반환**할 수 있도록 합니다.

---

### 📚 배경 지식

#### LPOP 명령어

```
LPOP <key>
```

리스트의 **첫 번째(왼쪽)** 요소를 제거하고 반환합니다.

```bash
RPUSH mylist "a" "b" "c"   # ["a", "b", "c"]
LPOP mylist                 # "a" 반환, 리스트: ["b", "c"]
LPOP mylist                 # "b" 반환, 리스트: ["c"]
```

#### RPOP 명령어

```
RPOP <key>
```

리스트의 **마지막(오른쪽)** 요소를 제거하고 반환합니다.

```bash
RPUSH mylist "a" "b" "c"   # ["a", "b", "c"]
RPOP mylist                 # "c" 반환, 리스트: ["a", "b"]
RPOP mylist                 # "b" 반환, 리스트: ["a"]
```

#### 응답 형식

| 상황 | 응답 |
|------|------|
| 요소가 있는 경우 | Bulk String (`$N\r\n<data>\r\n`) |
| 빈 리스트 또는 키 없음 | Null Bulk String (`$-1\r\n`) |

#### 스택과 큐 구현

```bash
# 스택 (LIFO): LPUSH + LPOP
LPUSH stack "a"
LPUSH stack "b"
LPUSH stack "c"
LPOP stack   # "c" (마지막에 넣은 것이 먼저 나옴)

# 큐 (FIFO): RPUSH + LPOP
RPUSH queue "a"
RPUSH queue "b"
RPUSH queue "c"
LPOP queue   # "a" (먼저 넣은 것이 먼저 나옴)
```

#### 빈 리스트 자동 삭제

Redis는 모든 요소가 제거된 리스트를 **자동으로 삭제**합니다:

```bash
RPUSH mylist "only"   # 리스트 생성
LPOP mylist           # "only" 반환, 리스트 삭제됨
EXISTS mylist         # 0 (키가 존재하지 않음)
```

---

### ✅ 통과 조건

- `LPOP <key>` 명령어로 첫 번째 요소를 제거하고 반환해야 합니다
- `RPOP <key>` 명령어로 마지막 요소를 제거하고 반환해야 합니다
- 빈 리스트나 존재하지 않는 키에 대해 `$-1\r\n`을 반환해야 합니다
- 마지막 요소가 제거되면 리스트(키)가 삭제되어야 합니다

---

### 💡 힌트

```kotlin
"LPOP" -> {
    if (command.args.isEmpty()) {
        writer.write("-ERR wrong number of arguments for 'lpop' command\r\n")
        writer.flush()
        return
    }
    
    val key = command.args[0]
    
    // 타입 충돌 체크
    if (stringStore.containsKey(key)) {
        writer.write("-WRONGTYPE Operation against a key holding the wrong kind of value\r\n")
        writer.flush()
        return
    }
    
    val list = listStore[key]
    
    if (list == null || list.isEmpty()) {
        writer.write("\$-1\r\n")
        writer.flush()
        return
    }
    
    // 첫 번째 요소 제거 및 반환
    val element = list.removeAt(0)
    
    // 리스트가 비었으면 키 삭제
    if (list.isEmpty()) {
        listStore.remove(key)
    }
    
    writer.write("\$${element.length}\r\n$element\r\n")
}

"RPOP" -> {
    if (command.args.isEmpty()) {
        writer.write("-ERR wrong number of arguments for 'rpop' command\r\n")
        writer.flush()
        return
    }
    
    val key = command.args[0]
    
    // 타입 충돌 체크
    if (stringStore.containsKey(key)) {
        writer.write("-WRONGTYPE Operation against a key holding the wrong kind of value\r\n")
        writer.flush()
        return
    }
    
    val list = listStore[key]
    
    if (list == null || list.isEmpty()) {
        writer.write("\$-1\r\n")
        writer.flush()
        return
    }
    
    // 마지막 요소 제거 및 반환
    val element = list.removeAt(list.size - 1)
    
    // 리스트가 비었으면 키 삭제
    if (list.isEmpty()) {
        listStore.remove(key)
    }
    
    writer.write("\$${element.length}\r\n$element\r\n")
}
```

---

### 🧪 테스트 방법

```bash
# 리스트 생성
redis-cli RPUSH mylist "a" "b" "c" "d" "e"
# 예상 출력: (integer) 5

# LPOP 테스트 (앞에서 제거)
redis-cli LPOP mylist
# 예상 출력: "a"

redis-cli LRANGE mylist 0 -1
# 예상 출력: "b", "c", "d", "e"

# RPOP 테스트 (뒤에서 제거)
redis-cli RPOP mylist
# 예상 출력: "e"

redis-cli LRANGE mylist 0 -1
# 예상 출력: "b", "c", "d"

# 빈 리스트에서 POP
redis-cli RPUSH templist "only"
# 예상 출력: (integer) 1

redis-cli LPOP templist
# 예상 출력: "only"

redis-cli LPOP templist
# 예상 출력: (nil)

# 존재하지 않는 키
redis-cli LPOP nonexistent
# 예상 출력: (nil)

redis-cli RPOP nonexistent
# 예상 출력: (nil)

# 큐 동작 테스트 (FIFO)
redis-cli RPUSH queue "first" "second" "third"
redis-cli LPOP queue  # "first"
redis-cli LPOP queue  # "second"
redis-cli LPOP queue  # "third"
```

---

### 🤔 생각해볼 점

1. **원자성**: `LPOP`은 "읽기 + 삭제"를 하나의 원자적 연산으로 수행합니다. 왜 이것이 중요할까요? (힌트: 여러 워커가 같은 큐를 처리하는 상황)

2. **시간 복잡도**: `LPOP`과 `RPOP`의 시간 복잡도는 얼마일까요? `ArrayList`와 `LinkedList`에서 각각 어떻게 달라질까요?

---

### ➡️ 다음 단계

Stage 9에서는 한 번에 **여러 요소를 제거**하는 기능을 구현합니다.
