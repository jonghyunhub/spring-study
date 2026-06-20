package io.jonghyun.Redis.product

data class RedisTemplateInfo(
    val keySerializer: String,
    val valueSerializer: String,
    val hashKeySerializer: String,
    val hashValueSerializer: String,
    val valueOpsClassName: String,
    val hashOpsClassName: String
)