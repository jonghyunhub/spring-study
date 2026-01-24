# Redis Lists 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 리스트 생성하기 | ⬤○○ 쉬움 |
| 2 | 요소 추가하기 (RPUSH) | ⬤○○ 쉬움 |
| **3** | **여러 요소 추가하기** | ⬤○○ 쉬움 |
| 4 | 요소 조회하기 - 양수 인덱스 (LRANGE) | ⬤○○ 쉬움 |
| 5 | 요소 조회하기 - 음수 인덱스 | ⬤○○ 쉬움 |
| 6 | 앞에 요소 추가하기 (LPUSH) | ⬤○○ 쉬움 |
| 7 | 리스트 길이 조회하기 (LLEN) | ⬤○○ 쉬움 |
| 8 | 요소 제거하기 (LPOP/RPOP) | ⬤○○ 쉬움 |
| 9 | 여러 요소 제거하기 | ⬤○○ 쉬움 |
| 10 | 블로킹 조회 (BLPOP/BRPOP) | ⬤⬤○ 보통 |
| 11 | 타임아웃이 있는 블로킹 조회 | ⬤⬤○ 보통 |

---

## Stage 3: 여러 요소 추가하기

### 🎯 목표

`RPUSH` 명령어가 **여러 요소를 한 번에 추가**할 수 있도록 확장합니다. 이는 네트워크 왕복을 줄여 성능을 향상시킵니다.

---

### 📚 배경 지식

#### 다중 요소 RPUSH

```
RPUSH <key> <element1> [element2] [element3] ...
```

한 번의 명령어로 여러 요소를 추가할 수 있습니다.

```bash
RPUSH mylist "a" "b" "c"
# 리스트: ["a", "b", "c"]
# 응답: 3 (추가 후 총 길이)
```

#### 성능상의 이점

개별 RPUSH를 3번 호출하는 것보다 한 번에 3개를 추가하는 것이 훨씬 효율적입니다:

```
# 비효율적 (네트워크 왕복 3회)
RPUSH mylist "a"
RPUSH mylist "b"
RPUSH mylist "c"

# 효율적 (네트워크 왕복 1회)
RPUSH mylist "a" "b" "c"
```

#### RESP 프로토콜 예시

```
*5\r\n
$5\r\nRPUSH\r\n
$6\r\nmylist\r\n
$1\r\na\r\n
$1\r\nb\r\n
$1\r\nc\r\n
```

---

### ✅ 통과 조건

- `RPUSH <key> <e1> <e2> <e3> ...` 형식으로 여러 요소를 추가할 수 있어야 합니다
- 요소들은 주어진 순서대로 리스트 끝에 추가되어야 합니다
- 응답은 추가 후 리스트의 **총 길이**여야 합니다

---

### 💡 힌트

인자 리스트의 두 번째 요소부터 모두 추가합니다.

```kotlin
"RPUSH" -> {
    if (command.args.size < 2) {
        writer.write("-ERR wrong number of arguments for 'rpush' command\r\n")
        writer.flush()
        return
    }
    
    val key = command.args[0]
    val elements = command.args.drop(1)  // 첫 번째(key)를 제외한 모든 요소
    
    // 타입 충돌 체크
    if (stringStore.containsKey(key)) {
        writer.write("-WRONGTYPE Operation against a key holding the wrong kind of value\r\n")
        writer.flush()
        return
    }
    
    val list = listStore.computeIfAbsent(key) {
        java.util.Collections.synchronizedList(mutableListOf())
    }
    
    // 모든 요소를 순서대로 추가
    list.addAll(elements)
    
    writer.write(":${list.size}\r\n")
}
```

---

### 🧪 테스트 방법

```bash
# 한 번에 여러 요소 추가
redis-cli RPUSH mylist "a" "b" "c"
# 예상 출력: (integer) 3

# 기존 리스트에 추가
redis-cli RPUSH mylist "d" "e"
# 예상 출력: (integer) 5

# 단일 요소도 여전히 동작
redis-cli RPUSH mylist "f"
# 예상 출력: (integer) 6

# 새 리스트에 한 번에 여러 요소
redis-cli RPUSH newlist "x" "y" "z"
# 예상 출력: (integer) 3
```

---

### 🤔 생각해볼 점

1. **원자성(Atomicity)**: 여러 요소를 추가하는 중 에러가 발생하면 어떻게 해야 할까요? 일부만 추가된 상태로 남겨야 할까요, 아니면 롤백해야 할까요?

2. **순서 보장**: `RPUSH mylist "a" "b" "c"`에서 요소들이 반드시 a, b, c 순서로 추가되어야 합니다. 멀티스레드 환경에서 이 순서가 깨질 수 있는 상황은 무엇일까요?

---

### ➡️ 다음 단계

Stage 4에서는 `LRANGE` 명령어로 리스트의 **요소들을 조회**하는 기능을 구현합니다.
