package io.jonghyun.stubservice.control

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/control")
class StubControlController {

    @PostMapping("/fail")
    fun fail(): String {
        StubStateHolder.mode = StubMode.FAIL
        return "mode: FAIL — 이후 모든 요청에 500 반환"
    }

    @PostMapping("/slow")
    fun slow(): String {
        StubStateHolder.mode = StubMode.SLOW
        return "mode: SLOW — 이후 모든 요청에 ${StubStateHolder.slowDelayMs}ms 지연 후 응답"
    }

    @PostMapping("/recover")
    fun recover(): String {
        StubStateHolder.mode = StubMode.NORMAL
        return "mode: NORMAL — 정상 응답"
    }

    @GetMapping("/status")
    fun status(): String = "현재 모드: ${StubStateHolder.mode}"
}
