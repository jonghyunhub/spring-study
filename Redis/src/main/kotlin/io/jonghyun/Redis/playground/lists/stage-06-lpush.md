# Redis Lists 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 리스트 생성하기 | ⬤○○ 쉬움 |
| 2 | 요소 추가하기 (RPUSH) | ⬤○○ 쉬움 |
| 3 | 여러 요소 추가하기 | ⬤○○ 쉬움 |
| 4 | 요소 조회하기 - 양수 인덱스 (LRANGE) | ⬤○○ 쉬움 |
| 5 | 요소 조회하기 - 음수 인덱스 | ⬤○○ 쉬움 |
| **6** | **앞에 요소 추가하기 (LPUSH)** | ⬤○○ 쉬움 |
| 7 | 리스트 길이 조회하기 (LLEN) | ⬤○○ 쉬움 |
| 8 | 요소 제거하기 (LPOP/RPOP) | ⬤○○ 쉬움 |
| 9 | 여러 요소 제거하기 | ⬤○○ 쉬움 |
| 10 | 블로킹 조회 (BLPOP/BRPOP) | ⬤⬤○ 보통 |
| 11 | 타임아웃이 있는 블로킹 조회 | ⬤⬤○ 보통 |

---

## Stage 6: 앞에 요소 추가하기 (LPUSH)

### 🎯 목표

`LPUSH` 명령어를 구현하여 리스트의 **왼쪽(앞)** 에 요소를 추가할 수 있도록 합니다.

---

### 📚 배경 지식

#### LPUSH vs RPUSH

| 명령어 | 추가 위치 | 용도 |
|--------|----------|------|
| LPUSH | 왼쪽(앞) | 스택, 최신 데이터 우선 |
| RPUSH | 오른쪽(끝) | 큐, 삽입 순서 유지 |

```bash
# RPUSH: 끝에 추가
RPUSH mylist "a" "b" "c"
# 결과: ["a", "b", "c"]

# LPUSH: 앞에 추가
LPUSH mylist "x" "y" "z"
# 결과: ["z", "y", "x", "a", "b", "c"]
```

#### LPUSH의 삽입 순서

**중요!** 여러 요소를 LPUSH하면 **역순**으로 들어갑니다:

```bash
LPUSH mylist "1" "2" "3"
# 과정:
# [] → LPUSH "1" → ["1"]
# ["1"] → LPUSH "2" → ["2", "1"]
# ["2", "1"] → LPUSH "3" → ["3", "2", "1"]
#
# 최종 결과: ["3", "2", "1"]
```

각 요소가 **하나씩 순서대로 앞에 추가**되기 때문입니다.

#### 실무 활용

```bash
# 최근 활동 로그 (최신이 맨 앞)
LPUSH user:123:activity "viewed product A"
LPUSH user:123:activity "added to cart"
LPUSH user:123:activity "purchased"

# 최근 10개 활동 조회
LRANGE user:123:activity 0 9
```

---

### ✅ 통과 조건

- `LPUSH <key> <element>` 명령어로 리스트 앞에 요소를 추가할 수 있어야 합니다
- `LPUSH <key> <e1> <e2> <e3>` 형식으로 여러 요소를 추가할 수 있어야 합니다
- 여러 요소 추가 시 왼쪽부터 순서대로 추가되어 결과적으로 역순이 되어야 합니다
- 응답은 추가 후 리스트의 총 길이여야 합니다

---

### 💡 힌트

리스트의 0번 인덱스에 삽입합니다. 여러 요소는 순서대로 하나씩 삽입합니다.

```kotlin
"LPUSH" -> {
    if (command.args.size < 2) {
        writer.write("-ERR wrong number of arguments for 'lpush' command\r\n")
        writer.flush()
        return
    }
    
    val key = command.args[0]
    val elements = command.args.drop(1)
    
    // 타입 충돌 체크
    if (stringStore.containsKey(key)) {
        writer.write("-WRONGTYPE Operation against a key holding the wrong kind of value\r\n")
        writer.flush()
        return
    }
    
    val list = listStore.computeIfAbsent(key) {
        java.util.Collections.synchronizedList(mutableListOf())
    }
    
    // 각 요소를 순서대로 맨 앞에 삽입
    // "a" "b" "c" → ["a"] → ["b", "a"] → ["c", "b", "a"]
    for (element in elements) {
        list.add(0, element)
    }
    
    writer.write(":${list.size}\r\n")
}
```

**또는 더 효율적인 방법:**
```kotlin
// 요소들을 역순으로 한 번에 앞에 삽입
list.addAll(0, elements.reversed())
```

---

### 🧪 테스트 방법

```bash
# 기본 LPUSH
redis-cli LPUSH mylist "first"
# 예상 출력: (integer) 1

redis-cli LPUSH mylist "second"
# 예상 출력: (integer) 2

redis-cli LRANGE mylist 0 -1
# 예상 출력:
# 1) "second"
# 2) "first"

# 여러 요소 LPUSH (역순으로 들어감)
redis-cli LPUSH mylist "a" "b" "c"
# 예상 출력: (integer) 5

redis-cli LRANGE mylist 0 -1
# 예상 출력:
# 1) "c"
# 2) "b"
# 3) "a"
# 4) "second"
# 5) "first"

# 새 리스트에 여러 요소
redis-cli LPUSH newlist "x" "y" "z"
# 예상 출력: (integer) 3

redis-cli LRANGE newlist 0 -1
# 예상 출력:
# 1) "z"
# 2) "y"
# 3) "x"
```

---

### 🤔 생각해볼 점

1. **스택 구현**: `LPUSH` + `LPOP`을 조합하면 스택(LIFO)이 됩니다. 어떻게 동작할까요?

2. **큐 구현**: `RPUSH` + `LPOP`을 조합하면 큐(FIFO)가 됩니다. 메시지 큐로 어떻게 활용할 수 있을까요?

3. **자료구조 선택**: `ArrayList`에서 `add(0, element)`는 O(n) 연산입니다. `LinkedList`를 사용하면 O(1)이 됩니다. Redis는 내부적으로 어떤 자료구조를 사용할까요?

---

### ➡️ 다음 단계

Stage 7에서는 `LLEN` 명령어로 리스트의 **길이를 조회**하는 기능을 구현합니다.
