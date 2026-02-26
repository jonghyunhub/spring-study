# jonghyun-spring-study

새로운 기술을 실습 및 실험을 통해 학습하고, 동작 원리와 숙련도를 높이기 위한 프로젝트입니다.

---

<details>
<summary> Claude Code 스킬 활용 가이드 </summary>

이 프로젝트에서 Claude Code와 함께 학습할 때 아래 스킬을 활용하면 효과적입니다.

### 새 주제 실습을 시작할 때 — `brainstorming`

실습 전에 무엇을 검증할지, 어떤 시나리오를 실험할지 먼저 설계합니다.

```
"Transaction Isolation Level 실습을 시작하고 싶어"
→ brainstorming 스킬이 실험 시나리오와 검증 기준을 먼저 정리해줍니다
```

> **참고:** 학습 범위가 넓거나 어디서부터 시작해야 할지 막막할 때 특히 유용합니다.

### 실험 코드를 작성할 때 — `test-driven-development`

검증하고 싶은 동작을 테스트로 먼저 정의하고, 그 결과로 동작 원리를 확인합니다.

```
"Optimistic Lock과 Pessimistic Lock의 충돌 처리 방식 차이를 코드로 확인하고 싶어"
→ 테스트 케이스를 먼저 작성하고, 실행 결과로 동작을 검증합니다
```

> **참고:** 이 프로젝트는 테스트 코드 기반으로 실습하는 구조이므로 대부분의 실습에 적합합니다.

### 예상과 다르게 동작할 때 — `systematic-debugging`

단순히 코드를 고치는 것이 아니라, 왜 그렇게 동작하는지 원인을 추적합니다.

```
"Gap Lock이 예상한 범위와 다르게 잡혀"
→ 재현 → 원인 분석 → 동작 원리 이해 순서로 접근합니다
```

> **참고:** 버그를 빠르게 수정하는 것보다 동작 원리를 이해하는 것이 목표이므로, 예상 밖의 결과가 나왔을 때 꼭 활용하세요.

### 여러 주제를 체계적으로 실습할 때 — `writing-plans`

실습 순서와 단계별 검증 기준을 포함한 학습 계획을 수립합니다.

```
"Covering Index 실습 계획을 세워줘"
→ 실습 단계, 검증 방법, 예상 결과를 포함한 계획을 만들어줍니다
```

> **참고:** README의 `// todo` 항목들을 한꺼번에 계획할 때도 사용할 수 있습니다.

</details>

---

# MySQL

## Unique Index

[MySQL InnoDB Unique Index는 어떻게 정합성을 보장할까?](https://velog.io/@jonghyun3668/MySQL-InnoDB-Unique-Index%EB%8A%94-%EC%96%B4%EB%96%BB%EA%B2%8C-%EB%8F%99%EC%9E%91%ED%95%A0%EA%B9%8C-Feat.DeadLock)

## Named Lock
[Spring에서 MySQL NamedLock을 사용시 주의점 (Thread 와 Connection은 다르게 관리된다)](https://velog.io/@jonghyun3668/Spring%EC%97%90%EC%84%9C-MySQL-NamedLock%EC%9D%84-%EC%82%AC%EC%9A%A9%EC%8B%9C-%EC%A3%BC%EC%9D%98%EC%A0%90)

## InnoDB Record Lock(Pessimistic Lock)

[MySQL InnoDB 에서 Record Lock, Gap Lock, Next Key Lock 원리와 사용시 주의점(Record Lock 사용시에는 적절한 인덱스가 있는 컬럼으로 사용해야한다.)](https://velog.io/@jonghyun3668/MySQL-InnoDB%EC%97%90%EC%84%9C-Record-Gap-Lock-Next-Key-Lock-%EC%82%AC%EC%9A%A9%EC%8B%9C-%EC%A3%BC%EC%9D%98%EC%A0%90Pessimictic-Lock)

## Optimistic Lock vs Pessimistic Lock
// todo

## Transaction Isolation Level
// todo

## Covering Index
// todo

# Redis
// todo
