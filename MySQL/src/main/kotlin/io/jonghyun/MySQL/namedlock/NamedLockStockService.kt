package io.jonghyun.MySQL.namedlock

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NamedLockStockService(
    private val repository: NamedLockStockRepository,
) {

    companion object {
        val logger = LoggerFactory.getLogger(NamedLockStockService::class.java)!!
    }

    /**
     * [락 없이 동작하는 차감 로직] => Lost Update 발생
     */
    @Transactional
    fun decreaseStockWithOutLock(productId: Long, amount: Int) {
        val stock = repository.getStockByProductId(productId)
        stock.decrease(amount)
        repository.save(stock)
    }

    /**
     * [트랜잭션으로 묶는 방식] => 커밋되기 이전에 락을 해제하여 그 사이에 다른 요청이 들어오면 Lost Update 발생 가능
     *
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
            val lockResult = repository.getNamedLock(lockKey, lockTimeOutTime) // 락 획득 성공하면 1 리턴
            logger.info("GET_LOCK [$lockKey] result: $lockResult")

            // 락 획득 실패 시 예외 발생
            if (lockResult != 1) {
                throw IllegalStateException("Failed to acquire lock: $lockKey")
            }

            // 비즈니스 로직
            val stock = repository.getStockByProductId(productId)
            stock.decrease(amount)
            repository.save(stock)

        } finally {
            val releaseResult = repository.releaseNamedLock(lockKey)
            logger.info("RELEASE_LOCK [$lockKey] result: $releaseResult") // 락이 제대로 해제 됐으면 1 리턴
        }
    }


    /**
     * [트랜잭션 없이 사용하는 방식]
     *  이 방식의 문제점
     * - 스프링 트랜잭션이 없을때, 스프링 내부적으로 같은 메서드 내부의 쿼리 메서드를 같은 커넥션을 사용하도록 보장하지 않는다.
     */
    fun decreaseStockWithNamedLockWithOutTransactional(productId: Long, amount: Int) {
        val lockKey = "stock:$productId"
        val lockTimeOutTime = 100
        try {
            val lockResult = repository.getNamedLockWithConnectionId(lockKey, lockTimeOutTime)
            logger.info("GET_LOCK - connId: ${lockResult.connId}, result: ${lockResult.lockResult}")

            // 락 획득 실패 시 예외 발생
            if (lockResult.lockResult != 1L) {
                throw IllegalStateException("Failed to acquire lock: $lockKey")
            }

            // 비즈니스 로직
            val stock = repository.getStockByProductId(productId)

            Thread.sleep(100) // 비즈니스 로직에서 지연발생

            stock.decrease(amount)
            repository.save(stock)
        } finally {
            val releaseResult = repository.releaseNamedLockWithConnectionId(lockKey)
            logger.info("RELEASE_LOCK - connId: ${releaseResult.connId}, result: ${releaseResult.lockResult}") // 락이 제대로 해제 됐으면 1 리턴
        }
    }



}