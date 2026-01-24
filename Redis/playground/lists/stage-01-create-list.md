# Redis Lists 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| **1** | **리스트 생성하기** | ⬤○○ 쉬움 |
| 2 | 요소 추가하기 (RPUSH) | ⬤○○ 쉬움 |
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

## Stage 1: 리스트 생성하기

### 🎯 목표

Redis의 **List 자료구조**를 위한 저장소를 설계합니다. 기존 String 저장소와 별도로 List를 저장할 수 있는 구조를 만들고, `RPUSH` 명령어로 리스트를 생성할 수 있도록 합니다.

---

### 📚 배경 지식

#### Redis List란?

Redis List는 **문자열의 연결 리스트(Linked List)** 입니다. 양쪽 끝에서 O(1) 시간에 삽입/삭제가 가능합니다.

**주요 특징:**
- 순서가 보장됨 (삽입 순서 유지)
- 중복 요소 허용
- 최대 약 42억 개의 요소 저장 가능
- 양방향에서 push/pop 가능

**실무 활용 사례:**
- 메시지 큐 (작업 대기열)
- 최근 활동 로그
- 타임라인 피드
- 작업 스케줄링

#### RPUSH 명령어

```
RPUSH <key> <element>
```

리스트의 **오른쪽(끝)** 에 요소를 추가합니다. 키가 존재하지 않으면 새 리스트를 생성합니다.

**응답:** 추가 후 리스트의 길이 (Integer)

```bash
RPUSH mylist "hello"  # 응답: 1 (리스트 생성됨, 길이 1)
RPUSH mylist "world"  # 응답: 2 (길이 2)
```

#### RESP Integer 응답

```
:1\r\n      # Integer 1
:42\r\n     # Integer 42
```

---

### ✅ 통과 조건

- `RPUSH <key> <element>` 명령어로 새 리스트를 생성할 수 있어야 합니다
- 응답은 RESP Integer 형식(`:N\r\n`)이어야 합니다
- 존재하지 않는 키에 RPUSH하면 새 리스트가 생성되어야 합니다

---

### 💡 힌트

List 전용 저장소를 추가합니다. Kotlin의 `MutableList`를 사용하면 됩니다.

```kotlin
import java.util.concurrent.ConcurrentHashMap

// String 저장소 (기존)
data class StringEntry(
    val value: String,
    val expiresAt: Long?
)
val stringStore = ConcurrentHashMap<String, StringEntry>()

// List 저장소 (새로 추가)
val listStore = ConcurrentHashMap<String, MutableList<String>>()

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
            
            // 리스트가 없으면 새로 생성
            val list = listStore.computeIfAbsent(key) { 
                java.util.Collections.synchronizedList(mutableListOf()) 
            }
            list.add(element)
            
            // Integer 응답: 리스트 길이
            writer.write(":${list.size}\r\n")
        }
        // ... 다른 명령어들
    }
    writer.flush()
}
```

**동시성 주의사항:**
- `ConcurrentHashMap`은 Map 연산은 thread-safe하지만, 내부 List는 아닙니다
- `Collections.synchronizedList()`로 List도 thread-safe하게 만들어야 합니다

---

### 🧪 테스트 방법

```bash
# 새 리스트 생성
redis-cli RPUSH mylist "first"
# 예상 출력: (integer) 1

# 같은 리스트에 추가
redis-cli RPUSH mylist "second"
# 예상 출력: (integer) 2

# 다른 리스트 생성
redis-cli RPUSH anotherlist "hello"
# 예상 출력: (integer) 1
```

---

### 🤔 생각해볼 점

1. **타입 충돌**: 이미 String으로 저장된 키에 RPUSH를 하면 어떻게 해야 할까요?
   - Redis는 `-WRONGTYPE Operation against a key holding the wrong kind of value` 에러를 반환합니다

2. **자료구조 선택**: `ArrayList` vs `LinkedList` 중 어떤 것이 Redis List에 더 적합할까요?

---

### ➡️ 다음 단계

Stage 2에서는 한 번에 **여러 요소를 추가**하는 기능을 구현합니다.
