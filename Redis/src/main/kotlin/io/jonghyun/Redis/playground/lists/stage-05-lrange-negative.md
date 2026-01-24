# Redis Lists 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | 리스트 생성하기 | ⬤○○ 쉬움 |
| 2 | 요소 추가하기 (RPUSH) | ⬤○○ 쉬움 |
| 3 | 여러 요소 추가하기 | ⬤○○ 쉬움 |
| 4 | 요소 조회하기 - 양수 인덱스 (LRANGE) | ⬤○○ 쉬움 |
| **5** | **요소 조회하기 - 음수 인덱스** | ⬤○○ 쉬움 |
| 6 | 앞에 요소 추가하기 (LPUSH) | ⬤○○ 쉬움 |
| 7 | 리스트 길이 조회하기 (LLEN) | ⬤○○ 쉬움 |
| 8 | 요소 제거하기 (LPOP/RPOP) | ⬤○○ 쉬움 |
| 9 | 여러 요소 제거하기 | ⬤○○ 쉬움 |
| 10 | 블로킹 조회 (BLPOP/BRPOP) | ⬤⬤○ 보통 |
| 11 | 타임아웃이 있는 블로킹 조회 | ⬤⬤○ 보통 |

---

## Stage 5: 요소 조회하기 - 음수 인덱스

### 🎯 목표

`LRANGE` 명령어가 **음수 인덱스**를 처리할 수 있도록 확장합니다. 음수 인덱스는 리스트의 끝에서부터 요소를 참조합니다.

---

### 📚 배경 지식

#### 음수 인덱스란?

음수 인덱스는 리스트의 **끝에서부터** 요소를 참조합니다:

```
리스트: ["a", "b", "c", "d", "e"]
양수:     0    1    2    3    4
음수:    -5   -4   -3   -2   -1
```

- `-1`: 마지막 요소
- `-2`: 마지막에서 두 번째 요소
- `-N`: 마지막에서 N번째 요소

#### 실용적인 사용 예시

```bash
# 전체 리스트 조회 (가장 흔한 패턴!)
LRANGE mylist 0 -1

# 마지막 3개 요소
LRANGE mylist -3 -1

# 첫 번째부터 마지막에서 두 번째까지
LRANGE mylist 0 -2
```

#### 인덱스 변환 공식

음수 인덱스를 양수로 변환:
```
실제_인덱스 = 리스트_길이 + 음수_인덱스
```

예시 (리스트 길이 5):
- `-1` → `5 + (-1)` = `4`
- `-3` → `5 + (-3)` = `2`

---

### ✅ 통과 조건

- `LRANGE mylist 0 -1`로 전체 리스트를 조회할 수 있어야 합니다
- `LRANGE mylist -3 -1`로 마지막 3개 요소를 조회할 수 있어야 합니다
- 양수와 음수 인덱스를 혼합해서 사용할 수 있어야 합니다
- 범위를 벗어난 음수 인덱스도 유연하게 처리해야 합니다

---

### 💡 힌트

인덱스 정규화 함수를 만들어 음수를 양수로 변환합니다.

```kotlin
/**
 * 음수 인덱스를 양수로 변환하고, 범위를 리스트 크기에 맞게 조정
 */
fun normalizeIndex(index: Int, size: Int): Int {
    return if (index < 0) {
        // 음수 인덱스: size + index
        // 예: size=5, index=-1 → 5 + (-1) = 4
        (size + index).coerceAtLeast(0)
    } else {
        index
    }
}

"LRANGE" -> {
    if (command.args.size < 3) {
        writer.write("-ERR wrong number of arguments for 'lrange' command\r\n")
        writer.flush()
        return
    }
    
    val key = command.args[0]
    val startInput = command.args[1].toIntOrNull() ?: 0
    val stopInput = command.args[2].toIntOrNull() ?: -1
    
    // 타입 충돌 체크
    if (stringStore.containsKey(key)) {
        writer.write("-WRONGTYPE Operation against a key holding the wrong kind of value\r\n")
        writer.flush()
        return
    }
    
    val list = listStore[key]
    
    if (list == null || list.isEmpty()) {
        writer.write("*0\r\n")
        writer.flush()
        return
    }
    
    val size = list.size
    
    // 음수 인덱스 변환
    var start = normalizeIndex(startInput, size)
    var stop = normalizeIndex(stopInput, size)
    
    // 범위 조정
    start = start.coerceIn(0, size - 1)
    stop = stop.coerceIn(0, size - 1)
    
    // start가 stop보다 크면 빈 배열
    if (start > stop) {
        writer.write("*0\r\n")
        writer.flush()
        return
    }
    
    // 범위 내 요소 추출 (stop은 inclusive이므로 +1)
    val elements = list.subList(start, stop + 1)
    
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

# 전체 리스트 조회 (가장 중요한 패턴!)
redis-cli LRANGE mylist 0 -1
# 예상 출력:
# 1) "a"
# 2) "b"
# 3) "c"
# 4) "d"
# 5) "e"

# 마지막 요소만
redis-cli LRANGE mylist -1 -1
# 예상 출력:
# 1) "e"

# 마지막 3개
redis-cli LRANGE mylist -3 -1
# 예상 출력:
# 1) "c"
# 2) "d"
# 3) "e"

# 양수와 음수 혼합
redis-cli LRANGE mylist 1 -2
# 예상 출력:
# 1) "b"
# 2) "c"
# 3) "d"

# 처음부터 마지막에서 두 번째까지
redis-cli LRANGE mylist 0 -2
# 예상 출력:
# 1) "a"
# 2) "b"
# 3) "c"
# 4) "d"

# 범위 초과 음수 인덱스
redis-cli LRANGE mylist -100 -1
# 예상 출력: 전체 리스트 (시작이 0으로 조정됨)
```

---

### 🤔 생각해볼 점

1. **`LRANGE key 0 -1` 패턴**: 이것이 Redis에서 가장 많이 사용되는 LRANGE 패턴입니다. 왜 그럴까요?

2. **엣지 케이스**: `LRANGE mylist -1 -3`처럼 start가 stop보다 뒤에 있으면 어떻게 해야 할까요? (힌트: 빈 배열)

3. **Python과의 차이**: Python 슬라이싱은 `list[0:-1]`이 마지막 요소를 **제외**하지만, Redis는 **포함**합니다. 왜 이런 차이가 있을까요?

---

### ➡️ 다음 단계

Stage 6에서는 `LPUSH` 명령어로 리스트의 **앞(왼쪽)** 에 요소를 추가하는 기능을 구현합니다.
