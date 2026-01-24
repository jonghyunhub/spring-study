package io.jonghyun.MySQL.namedlock

data class NamedLockResult(
      val lockResult: Long,
      val connId: Long
  )
