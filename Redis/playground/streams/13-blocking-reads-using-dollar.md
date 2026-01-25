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
| 8 | `+` 를 사용한 쿼리 | ⬤○○ 쉬움 |
| 9 | XREAD로 단일 스트림 조회 | ⬤⬤○ 보통 |
| 10 | XREAD로 다중 스트림 조회 | ⬤⬤○ 보통 |
| 11 | 블로킹 읽기 | ⬤⬤⬤ 어려움 |
| 12 | 타임아웃 없는 블로킹 읽기 | ⬤⬤○ 보통 |
| **13** | **`$` 를 사용한 블로킹 읽기** | ⬤○○ 쉬움 |

---

## Stage 13: `$` 를 사용한 블로킹 읽기

### 🎯 목표

`XREAD` 명령어에서 **`$` 특수 ID**를 인식하여 **현재 시점 이후의 새 엔트리만 대기**하도록 구현합니다.

---

### 📚 배경 지식

#### `$` 특수 ID란?

`$`는 **"현재 스트림의 마지막 ID"**를 의미합니다. `XREAD`에서 사용하면 기존 데이터는 무시하고 **새로 추가되는 엔트리만** 받습니다.

```
XREAD BLOCK 0 STREAMS mystream $
                               ↑
                    "지금부터 새로 오는 것만"
```

#### 사용 시나리오

| ID | 의미 | 사용 시나리오 |
|----|------|---------------|
| `0-0` | 처음부터 | 과거 데이터 포함 전체 처리 |
| `1234567-0` | 특정 ID 이후 | 중단점부터 재개 |
| `$` | 지금부터 | 실시간 알림, 새 이벤트만 처리 |

#### 0-0 vs $ 비교

```bash
# 스트림에 이미 3개의 엔트리가 있는 상황

# 0-0: 기존 3개 + 새 엔트리
XREAD BLOCK 0 STREAMS mystream 0-0
# -> 즉시 기존 3개 반환

# $: 새 엔트리만 대기
XREAD BLOCK 0 STREAMS mystream $
# -> 대기... (새 데이터 올 때까지)
```

---

### ✅ 통과 조건

- `XREAD STREAMS mystream $` 형식을 처리할 수 있어야 합니다
- `$`는 현재 스트림의 마지막 ID로 해석되어야 합니다
- 블로킹과 함께 사용 시 새 엔트리만 대기해야 합니다

---

### 💡 힌트

ID 파싱 시 `$`를 현재 마지막 ID로 변환합니다:

```kotlin
fun resolveXReadId(id: String, streamKey: String): StreamId {
    if (id == "$") {
        // 스트림의 마지막 ID 반환
        val stream = getStream(streamKey)
        return if (stream == null || stream.entries.isEmpty()) {
            StreamId(0, 0)  // 빈 스트림이면 0-0
        } else {
            StreamId.parse(stream.entries.last().id)
        }
    }
    return StreamId.parse(id)
}

fun handleXRead(args: List<String>): String {
    // ... BLOCK, STREAMS 파싱 ...
    
    // 각 스트림별로 ID 변환
    val resolvedIds = mutableMapOf<String, StreamId>()
    for (i in 0 until count) {
        val key = keys[i]
        val idArg = ids[i]
        resolvedIds[key] = resolveXReadId(idArg, key)
    }
    
    // 나머지 로직은 동일...
}
```

**주의**: `$`는 `XREAD` 명령어 **실행 시점**의 마지막 ID입니다. 이후 블로킹 중에 추가된 엔트리는 반환됩니다.

---

### 🧪 테스트 방법

```bash
# 기존 데이터 추가
127.0.0.1:6379> XADD mystream * old data
"1609459200000-0"
127.0.0.1:6379> XADD mystream * old data2
"1609459200001-0"

# 터미널 1: $ 로 새 데이터만 대기
127.0.0.1:6379> XREAD BLOCK 5000 STREAMS mystream $
# ... 대기 중 (기존 데이터는 무시) ...

# 터미널 2: 새 데이터 추가
127.0.0.1:6379> XADD mystream * new data
"1609459200100-0"

# 터미널 1: 새 데이터만 반환
1) 1) "mystream"
   2) 1) 1) "1609459200100-0"
         2) 1) "new"
            2) "data"
```

비교: `0-0` 사용 시

```bash
127.0.0.1:6379> XREAD STREAMS mystream 0-0
# -> 기존 데이터 즉시 반환 (대기 없음)
1) 1) "mystream"
   2) 1) 1) "1609459200000-0"
         2) 1) "old"
            2) "data"
      2) 1) "1609459200001-0"
         2) 1) "old"
            2) "data2"
```

---

### 🤔 생각해볼 점

#### 실시간 알림 시스템

```kotlin
// 새 주문만 실시간으로 처리
while (true) {
    // $ 사용: 지금부터 새 주문만
    val orders = redis.xread("BLOCK", "0", "STREAMS", "orders", "$")
    
    for (order in orders) {
        sendNotification(order)
    }
}
```

**주의**: 이 패턴은 첫 실행 후 새 이벤트만 받습니다. 프로그램 재시작 시 놓친 이벤트가 있을 수 있습니다.

#### Consumer Group과의 차이

`$`는 간단하지만 한계가 있습니다:
- 여러 소비자가 **같은 이벤트를 중복 처리**할 수 있음
- 놓친 이벤트 **재처리가 어려움**

실제 프로덕션에서는 **Consumer Group** (`XGROUP`, `XREADGROUP`)을 사용합니다.

---

### 🎉 축하합니다!

Redis Streams의 핵심 기능을 모두 구현했습니다!

#### 지금까지 구현한 것

1. ✅ TYPE 명령어
2. ✅ XADD (명시적 ID, 부분 자동 생성, 완전 자동 생성)
3. ✅ ID 유효성 검증
4. ✅ XRANGE (범위 쿼리, `-`, `+`)
5. ✅ XREAD (단일/다중 스트림, 블로킹, `$`)

#### 다음 도전 과제

더 깊이 학습하고 싶다면:

- **Consumer Groups**: `XGROUP CREATE`, `XREADGROUP`, `XACK`
- **스트림 관리**: `XTRIM`, `XDEL`, `XLEN`, `XINFO`
- **Pending Entries**: 처리 실패한 메시지 재처리
- **Claim 메커니즘**: 죽은 소비자의 메시지 인계
