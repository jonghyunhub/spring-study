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
| 8 | 요소 제거하기 (LPOP/RPOP) | ⬤○○ 쉬움 |
| **9** | **여러 요소 제거하기** | ⬤○○ 쉬움 |
| 10 | 블로킹 조회 (BLPOP/BRPOP) | ⬤⬤○ 보통 |
| 11 | 타임아웃이 있는 블로킹 조회 | ⬤⬤○ 보통 |

---

## Stage 9: 여러 요소 제거하기

### 🎯 목표

`LPOP`과 `RPOP` 명령어에 **count 인자**를 추가하여 한 번에 여러 요소를 제거할 수 있도록 합니다.

---

### 📚 배경 지식

#### count 옵션 (Redis 6.2+)

```
LPOP <key> [count]
RPOP <key> [count]
```

`count`를 지정하면 해당 개수만큼의 요소를 제거하고 **배열로 반환**합니다.

```bash
RPUSH mylist "a" "b" "c" "d" "e"

LPOP mylist 3
# 응답: ["a", "b", "c"]
# 리스트: ["d", "e"]

RPOP mylist 2
# 응답: ["e", "d"]  (RPOP은 역순!)
# 리스트: []
```

#### 응답 형식 차이

| 호출 방식 | 응답 형식 |
|----------|----------|
| `LPOP key` (count 없음) | Bulk String 또는 Null |
| `LPOP key 1` (count=1) | Array (요소 1개인 배열) |
| `LPOP key N` (count>1) | Array |

#### RPOP의 반환 순서

**주의!** `RPOP key count`는 **제거된 순서대로** 반환합니다:

```bash
# 리스트: ["a", "b", "c", "d", "e"]
RPOP mylist 3
# 제거 순서: "e" → "d" → "c"
# 응답: ["e", "d", "c"]
```

#### count가 리스트 크기보다 큰 경우

리스트에 있는 모든 요소만 반환합니다 (에러 아님):

```bash
RPUSH mylist "a" "b"
LPOP mylist 10
# 응답: ["a", "b"]  (2개만 있으므로 2개만 반환)
```

---

### ✅ 통과 조건

- `LPOP <key> <count>` 형식으로 여러 요소를 제거할 수 있어야 합니다
- `RPOP <key> <count>` 형식으로 여러 요소를 제거할 수 있어야 합니다
- count가 있으면 RESP Array 형식으로 응답해야 합니다
- count가 리스트 크기보다 크면 가능한 만큼만 반환해야 합니다
- 빈 리스트는 빈 배열(`*0\r\n`) 또는 Null을 반환해야 합니다
- count 없이 호출하면 이전처럼 단일 요소를 반환해야 합니다

---

### 💡 힌트

count 인자 유무에 따라 응답 형식을 다르게 처리합니다.

```kotlin
"LPOP" -> {
    if (command.args.isEmpty()) {
        writer.write("-ERR wrong number of arguments for 'lpop' command\r\n")
        writer.flush()
        return
    }
    
    val key = command.args[0]
    val count = command.args.getOrNull(1)?.toIntOrNull()
    
    // 타입 충돌 체크
    if (stringStore.containsKey(key)) {
        writer.write("-WRONGTYPE Operation against a key holding the wrong kind of value\r\n")
        writer.flush()
        return
    }
    
    val list = listStore[key]
    
    if (list == null || list.isEmpty()) {
        if (count != null) {
            writer.write("*0\r\n")  // count가 있으면 빈 배열
        } else {
            writer.write("\$-1\r\n")  // count가 없으면 Null
        }
        writer.flush()
        return
    }
    
    if (count == null) {
        // 기존 동작: 단일 요소 반환
        val element = list.removeAt(0)
        if (list.isEmpty()) listStore.remove(key)
        writer.write("\$${element.length}\r\n$element\r\n")
    } else {
        // 여러 요소 반환
        val actualCount = minOf(count, list.size)
        val removed = mutableListOf<String>()
        
        repeat(actualCount) {
            removed.add(list.removeAt(0))
        }
        
        if (list.isEmpty()) listStore.remove(key)
        
        // RESP Array 형식
        writer.write("*${removed.size}\r\n")
        for (element in removed) {
            writer.write("\$${element.length}\r\n$element\r\n")
        }
    }
}

"RPOP" -> {
    if (command.args.isEmpty()) {
        writer.write("-ERR wrong number of arguments for 'rpop' command\r\n")
        writer.flush()
        return
    }
    
    val key = command.args[0]
    val count = command.args.getOrNull(1)?.toIntOrNull()
    
    // 타입 충돌 체크
    if (stringStore.containsKey(key)) {
        writer.write("-WRONGTYPE Operation against a key holding the wrong kind of value\r\n")
        writer.flush()
        return
    }
    
    val list = listStore[key]
    
    if (list == null || list.isEmpty()) {
        if (count != null) {
            writer.write("*0\r\n")
        } else {
            writer.write("\$-1\r\n")
        }
        writer.flush()
        return
    }
    
    if (count == null) {
        // 기존 동작: 단일 요소 반환
        val element = list.removeAt(list.size - 1)
        if (list.isEmpty()) listStore.remove(key)
        writer.write("\$${element.length}\r\n$element\r\n")
    } else {
        // 여러 요소 반환 (뒤에서부터 제거, 제거 순서대로 반환)
        val actualCount = minOf(count, list.size)
        val removed = mutableListOf<String>()
        
        repeat(actualCount) {
            removed.add(list.removeAt(list.size - 1))
        }
        
        if (list.isEmpty()) listStore.remove(key)
        
        // RESP Array 형식
        writer.write("*${removed.size}\r\n")
        for (element in removed) {
            writer.write("\$${element.length}\r\n$element\r\n")
        }
    }
}
```

---

### 🧪 테스트 방법

```bash
# 리스트 생성
redis-cli RPUSH mylist "a" "b" "c" "d" "e"
# 예상 출력: (integer) 5

# LPOP with count
redis-cli LPOP mylist 2
# 예상 출력:
# 1) "a"
# 2) "b"

redis-cli LRANGE mylist 0 -1
# 예상 출력: "c", "d", "e"

# RPOP with count
redis-cli RPOP mylist 2
# 예상 출력:
# 1) "e"
# 2) "d"

redis-cli LRANGE mylist 0 -1
# 예상 출력: "c"

# count가 리스트 크기보다 큰 경우
redis-cli LPOP mylist 100
# 예상 출력:
# 1) "c"

# 빈 리스트에서 count와 함께 호출
redis-cli LPOP mylist 5
# 예상 출력: (empty array)

# 기존 동작도 유지되는지 확인
redis-cli RPUSH testlist "x" "y" "z"
redis-cli LPOP testlist
# 예상 출력: "x" (배열 아님)

redis-cli RPOP testlist
# 예상 출력: "z" (배열 아님)
```

---

### 🤔 생각해볼 점

1. **배치 처리**: 왜 한 번에 여러 요소를 가져오는 것이 효율적일까요? 네트워크 왕복 외에 다른 이점이 있을까요?

2. **원자성**: `LPOP mylist 3`은 3개의 요소를 원자적으로 제거합니다. 이것이 왜 중요할까요? (힌트: 여러 컨슈머가 같은 큐를 처리하는 상황)

3. **RESP 버전 호환**: count 옵션은 Redis 6.2에서 추가되었습니다. 이전 버전 클라이언트와의 호환성은 어떻게 유지될까요?

---

### ➡️ 다음 단계

Stage 10에서는 `BLPOP`과 `BRPOP` 명령어로 **블로킹 조회**를 구현합니다. 리스트가 비어있으면 요소가 추가될 때까지 대기하는 강력한 기능입니다!
