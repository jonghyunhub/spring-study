# Redis Lists 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 리스트 생성하기 | ⬤○○ 쉬움 |
| **2** | **요소 추가하기 (RPUSH)** | ⬤○○ 쉬움 |
| 3 | 여러 요소 추가하기 | ⬤○○ 쉬움 |
| 4 | 요소 조회하기 - 양수 인덱스 (LRANGE) | ⬤○○ 쉬움 |
| 5 | 요소 조회하기 - 음수 인덱스 | ⬤○○ 쉬움 |
| 6 | 앞에 요소 추가하기 (LPUSH) | ⬤○○ 쉬움 |
| 7 | 리스트 길이 조회하기 (LLEN) | ⬤○○ 쉬움 |
| 8 | 요소 제거하기 (LPOP/RPOP) | ⬤○○ 쉬움 |
| 9 | 여러 요소 제거하기 | ⬤○○ 쉬움 |
| 10 | 블로킹 조회 (BLPOP/BRPOP) | ⬤⬤○ 보통 |
| 11 | 타임아웃이 있는 블로킹 조회 | ⬤⬤○ 보통 |

---

## Stage 2: 요소 추가하기 (RPUSH)

### 🎯 목표

이전 단계에서 기본 RPUSH를 구현했습니다. 이번에는 **기존 리스트에 요소를 계속 추가**하는 동작이 올바르게 작동하는지 확인하고, 타입 충돌 처리를 추가합니다.

---

### 📚 배경 지식

#### 리스트의 동작 방식

RPUSH는 리스트의 **오른쪽(tail)** 에 요소를 추가합니다.

```
초기 상태: []
RPUSH mylist "a"  →  ["a"]
RPUSH mylist "b"  →  ["a", "b"]
RPUSH mylist "c"  →  ["a", "b", "c"]
```

요소는 항상 **끝에 추가**되므로 삽입 순서가 보존됩니다.

#### 타입 충돌 (Type Conflict)

Redis는 키마다 하나의 타입만 가질 수 있습니다. String 키에 List 명령어를 사용하면 에러가 발생합니다.

```bash
SET mykey "hello"
RPUSH mykey "world"
# 에러: WRONGTYPE Operation against a key holding the wrong kind of value
```

---

### ✅ 통과 조건

- 기존 리스트에 요소를 계속 추가할 수 있어야 합니다
- 매번 추가 후 리스트의 **현재 길이**를 반환해야 합니다
- String 타입 키에 RPUSH하면 WRONGTYPE 에러를 반환해야 합니다

---

### 💡 힌트

타입 체크 로직을 추가합니다.

```kotlin
fun handleCommand(command: Command, writer: java.io.BufferedWriter) {
    when (command.name) {
        "RPUSH" -> {
            if (command.args.size < 2) {
                writer.write("-ERR wrong number of arguments for 'rpush' command\r\n")
                writer.flush()
                return
            }
            
            val key = command.args[0]
            val element = command.args[1]
            
            // 타입 충돌 체크: String 저장소에 같은 키가 있는지 확인
            if (stringStore.containsKey(key)) {
                writer.write("-WRONGTYPE Operation against a key holding the wrong kind of value\r\n")
                writer.flush()
                return
            }
            
            val list = listStore.computeIfAbsent(key) {
                java.util.Collections.synchronizedList(mutableListOf())
            }
            list.add(element)
            
            writer.write(":${list.size}\r\n")
        }
        // ... 다른 명령어들
    }
    writer.flush()
}
```

---

### 🧪 테스트 방법

```bash
# 리스트에 순차적으로 요소 추가
redis-cli RPUSH mylist "first"
# 예상 출력: (integer) 1

redis-cli RPUSH mylist "second"
# 예상 출력: (integer) 2

redis-cli RPUSH mylist "third"
# 예상 출력: (integer) 3

redis-cli RPUSH mylist "fourth"
# 예상 출력: (integer) 4

# 타입 충돌 테스트
redis-cli SET stringkey "hello"
# 예상 출력: OK

redis-cli RPUSH stringkey "world"
# 예상 출력: (error) WRONGTYPE Operation against a key holding the wrong kind of value
```

---

### ➡️ 다음 단계

Stage 3에서는 한 번의 명령어로 **여러 요소를 동시에 추가**하는 기능을 구현합니다.
