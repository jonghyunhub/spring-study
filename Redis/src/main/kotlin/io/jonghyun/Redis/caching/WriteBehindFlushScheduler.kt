package io.jonghyun.Redis.caching

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class WriteBehindFlushScheduler(
    private val writeBehindService: WriteBehindService
) {
    // 5초마다 dirty-set에 등록된 항목을 DB에 일괄 반영
    @Scheduled(fixedDelay = 5000)
    fun flush() {
        writeBehindService.flushPendingUpdates()
    }
}
