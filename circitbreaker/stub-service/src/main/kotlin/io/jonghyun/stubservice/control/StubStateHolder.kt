package io.jonghyun.stubservice.control

enum class StubMode { NORMAL, FAIL, SLOW }

object StubStateHolder {
    @Volatile
    var mode: StubMode = StubMode.NORMAL
    val slowDelayMs: Long = 3000
}
