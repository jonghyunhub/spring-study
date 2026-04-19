package io.jonghyun.circitbreaker.config

import feign.Request
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class FeignConfig {

    // stub-service 응답 대기 시간 설정
    // slow-call 임계값(application.yml: slow-call-duration-threshold: 2s)보다 길게 설정해야
    // slow call이 circuit breaker에 실제로 카운트됩니다.
    @Bean
    fun feignRequestOptions(): Request.Options =
        Request.Options(
            Duration.ofSeconds(1),  // connect timeout
            Duration.ofSeconds(5),  // read timeout
            true,
        )
}
