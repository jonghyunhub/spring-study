package io.jonghyun.MySQL.lock

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource

@Service
class StockService(
    private val stockRepository: StockRepository,
) {

    companion object {
        val logger = LoggerFactory.getLogger(StockService::class.java)!!
    }

    /**
     * 락 없이 동작하는 차감 로직
     */
    @Transactional
    fun decreaseStockWithOutLock(productId: Long, amount: Int) {
        val stock = stockRepository.getStockByProductId(productId)
        stock.decrease(amount)
        stockRepository.save(stock)
    }

    /**
     * 이렇게 하면 안됨 why?
     * Named Lock 획득 -> 트랜잭션 시작 -> read -> 차감 로직 -> update -> 트랜잭션 종료 -> Named Lock 해제
     * 의 과정이어야하는데, 이 로직은
     * 트랜잭션 시작 -> Named Lock 획득 -> read -> 차감 로직 -> update -> Named Lock 해제 -> 트랜잭션 종료
     * 의 순서로 실행되어 제대로 동작하지 않음
     *
     * @Transactional  // ← 문제!
     *   fun decreaseStockWithNamedLockWrongTransaction(productId: Long, amount: Int) {
     *       try {
     *           stockRepository.getNamedLock(lockKey, lockTimeOutTime)  // ①
     *           val stock = stockRepository.getStockByProductId(productId)  // ②
     *           stock.decrease(amount)
     *           stockRepository.save(stock)  // ③
     *       } finally {
     *           stockRepository.releaseNamedLock(lockKey)  // ④
     *       }
     *   }  // ⑤ @Transactional COMMIT 발생
     *
     *   [치명적인 타이밍 이슈]
     *
     *   시간    Thread 1                              Thread 2
     *   ──────────────────────────────────────────────────────────────
     *   T1      @Transactional 시작
     *   T2      GET_LOCK('stock:1') ✓
     *   T3      SELECT (quantity=100)
     *   T4      UPDATE 준비 (메모리: 99)
     *   T5      save() 호출
     *   T6      RELEASE_LOCK('stock:1') ← 락 해제!
     *           ─────────────────────────────────────────────────────
     *   T7                                            @Transactional 시작
     *   T8                                            GET_LOCK('stock:1') ✓ 획득!
     *   T9                                            SELECT (quantity=100) ← 왜?
     *           ─────────────────────────────────────────────────────
     *   T10     COMMIT ← 이제야 quantity=99 반영!
     *           ─────────────────────────────────────────────────────
     *   T11                                           UPDATE (메모리: 99)
     *   T12                                           RELEASE_LOCK
     *   T13                                           COMMIT (quantity=99)
     *
     *   결과: 100 → 99 (Lost Update 발생!)
     *
     *   [문제 원인]
     *   1. RELEASE_LOCK()이 COMMIT보다 먼저 실행됨
     *     - finally 블록에서 락 해제 (T6)
     *     - 실제 트랜잭션 커밋은 메서드 종료 시 (T10)
     *   2. 격리 수준 때문에 T2가 T1의 변경사항을 못 봄
     *     - T2가 T9 시점에 SELECT할 때, T1은 아직 커밋 전
     *     - REPEATABLE READ에서는 T1의 미커밋 데이터를 볼 수 없음
     *     - 따라서 T2도 quantity=100을 읽음
     *   3. 결과적으로 Named Lock이 무용지물
     */
    @Transactional
    fun decreaseStockWithNamedLockWrongTransaction(productId: Long, amount: Int) {
        val lockKey = "stock:$productId"
        val lockTimeOutTime = 10
        try {
            val lockResult = stockRepository.getNamedLock(lockKey, lockTimeOutTime) // 락 획득 성공하면 1 리턴
            logger.info("GET_LOCK [$lockKey] result: $lockResult")

            // 락 획득 실패 시 예외 발생
            if (lockResult != 1) {
                throw IllegalStateException("Failed to acquire lock: $lockKey")
            }

            val stock = stockRepository.getStockByProductId(productId)
            stock.decrease(amount)
            stockRepository.save(stock)

        } finally {
            val releaseResult = stockRepository.releaseNamedLock(lockKey)
            logger.info("RELEASE_LOCK [$lockKey] result: $releaseResult") // 락이 제대로 해제 됐으면 1 리턴
        }
    }


    fun decreaseStockWithNamedLock(productId: Long, amount: Int) {
        val lockKey = "stock:$productId"
        val lockTimeOutTime = 10
        try {
            val lockResult = stockRepository.getNamedLock(lockKey, lockTimeOutTime) // 락 획득 성공하면 1 리턴
            logger.info("GET_LOCK [$lockKey] result: $lockResult")

            // 락 획득 실패 시 예외 발생
            if (lockResult != 1) {
                throw IllegalStateException("Failed to acquire lock: $lockKey")
            }

            val stock = stockRepository.getStockByProductId(productId)
            stock.decrease(amount)
            stockRepository.save(stock)
        } finally {
            val releaseResult = stockRepository.releaseNamedLock(lockKey)
            logger.info("RELEASE_LOCK [$lockKey] result: $releaseResult") // 락이 제대로 해제 됐으면 1 리턴
        }
    }

}