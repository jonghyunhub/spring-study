package io.jonghyun.MySQL.namedlock

@Target(AnnotationTarget.FUNCTION)
  @Retention(AnnotationRetention.RUNTIME)
  annotation class DistributedLock(
      /**
       * 락의 이름 (prefix)
       * 예: "user:update"
       */
      val key: String,

      /**
       * SpEL 표현식으로 동적 키 생성
       * 예: "#userId", "#request.email"
       */
      val dynamicKey: String = "",

      /**
       * 락 타임아웃 (초)
       */
      val timeout: Int = 10
  )