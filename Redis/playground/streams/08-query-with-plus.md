# Redis Streams 직접 구현하며 배우기

## 📋 Stage 목록

| Stage | 제목 | 난이도 |
|-------|------|--------|
| 1 | TYPE 명령어 구현하기 | ⬤○○ 쉬움 |
| 2 | 스트림 생성하기 | ⬤⬤○ 보통 |
| 3 | 엔트리 ID 유효성 검증 | ⬤○○ 쉬움 |
| 4 | 부분 자동 생성 ID | ⬤⬤○ 보통 |
| 5 | 완전 자동 생성 ID | ⬤⬤○ 보통 |
| 6 | 스트림에서 엔트리 조회하기 | ⬤⬤○ 보통 |
| 7 | `-` 를 사용한 쿼리 | ⬤○○ 쉬움 |
| **8** | **`+` 를 사용한 쿼리** | ⬤○○ 쉬움 |
| 9 | XREAD로 단일 스트림 조회 | ⬤⬤○ 보통 |
| 10 | XREAD로 다중 스트림 조회 | ⬤⬤○ 보통 |
| 11 | 블로킹 읽기 | ⬤⬤⬤ 어려움 |
| 12 | 타임아웃 없는 블로킹 읽기 | ⬤⬤○ 보통 |
| 13 | `$` 를 사용한 블로킹 읽기 | ⬤○○ 쉬움 |

---

## Stage 8: `+` 를 사용한 쿼리

### 🎯 목표

`XRANGE` 명령어에서 **`+`를 최대 ID로 인식**하도록 구현합니다.

---

### 📚 배경 지식

#### 특수 ID: `+`

`+`는 **가능한 가장 큰 ID**를 의미합니다. `XRANGE`의 끝 ID로 사용하면 스트림의 마지막 엔트리까지 조회합니다.

```
XRANGE mystream 2-0 +
                    ↑
              스트림의 끝까지
```

#### `-`와 `+` 조합

이 두 특수 ID를 조합하면 **스트림의 모든 엔트리**를 조회할 수 있습니다:

```
XRANGE mystream - +
                ↑ ↑
            처음 끝 = 전체
```

---

### ✅ 통과 조건

- `XRANGE mystream <start> +` 형식의 쿼리를 처리할 수 있어야 합니다
- `XRANGE mystream - +`로 전체 스트림을 조회할 수 있어야 합니다
- 스트림의 마지막 엔트리까지 포함되어야 합니다

---

### 💡 힌트

```kotlin
fun parseRangeId(id: String): StreamId {
    return when (id) {
        "-" -> StreamId(0, 0)                      // 최소 ID
        "+" -> StreamId(Long.MAX_VALUE, Long.MAX_VALUE)  // 최대 ID
        else -> StreamId.parse(id)
    }
}
```

`Long.MAX_VALUE`를 사용하면 어떤 실제 ID보다도 큰 값이 됩니다.

---

### 🧪 테스트 방법

```bash
redis-cli

# 테스트 데이터 추가
127.0.0.1:6379> XADD mystream 1-0 a 1
"1-0"
127.0.0.1:6379> XADD mystream 2-0 b 2
"2-0"
127.0.0.1:6379> XADD mystream 3-0 c 3
"3-0"

# "+"로 끝까지 조회
127.0.0.1:6379> XRANGE mystream 2-0 +
1) 1) "2-0"
   2) 1) "b"
      2) "2"
2) 1) "3-0"
   2) 1) "c"
      2) "3"

# "-"와 "+"로 전체 조회
127.0.0.1:6379> XRANGE mystream - +
1) 1) "1-0"
   2) 1) "a"
      2) "1"
2) 1) "2-0"
   2) 1) "b"
      2) "2"
3) 1) "3-0"
   2) 1) "c"
      2) "3"
```

---

### 🤔 생각해볼 점

#### XRANGE vs XREVRANGE

`XREVRANGE`는 `XRANGE`의 역순 버전입니다:

```bash
# XRANGE: 오름차순 (start → end)
XRANGE mystream - +

# XREVRANGE: 내림차순 (end → start)
# 인자 순서도 반대: end가 먼저!
XREVRANGE mystream + -
```

#### COUNT 옵션

실제 Redis는 `COUNT` 옵션으로 결과 수를 제한할 수 있습니다:

```bash
XRANGE mystream - + COUNT 2
# 처음 2개만 반환
```

---

### ➡️ 다음 단계

Stage 9에서는 **XREAD 명령어로 스트림을 조회**하는 기능을 구현합니다. `XRANGE`와 달리 `XREAD`는 여러 스트림을 동시에 조회할 수 있습니다.
