package io.jonghyun.Redis.api

data class CacheRequest(
    val name : String,
    val ttlSecond : Long?
)
