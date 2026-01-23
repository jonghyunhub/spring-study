package io.jonghyun.MySQL.lock


import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.sql.Connection
import javax.sql.DataSource

@Component
class NamedLockExecutor(
    private val dataSource: DataSource
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // @Transactional <- 이 메서드에 트랜잭셔널 추가하면 락 획득/해제 커넥션과 비즈니스 커넥션을 동일하게 고정시켜 버리니 주의!
    fun <T> executeWithLock(lockKey: String, timeout: Int = 10, action: () -> T): T {
        // 커넥션을 직접 획득해서 들고 있음
        val connection = dataSource.connection
        
        try {
            // 같은 connection 객체로 락 획득
            val acquired = acquireLock(connection, lockKey, timeout)
            if (!acquired) {
                throw IllegalStateException("Failed to acquire lock: $lockKey")
            }
            
            // 비즈니스 로직 실행 (별도 트랜잭션)
            return action()
            
        } finally {
            // 같은 connection 객체로 락 해제 → 동일 커넥션 보장
            releaseLock(connection, lockKey)
            connection.close()  // 풀에 반환
        }
    }
    
    private fun acquireLock(conn: Connection, key: String, timeout: Int): Boolean {
        conn.prepareStatement("SELECT GET_LOCK(?, ?)").use { stmt ->
            stmt.setString(1, key)
            stmt.setInt(2, timeout)
            val rs = stmt.executeQuery()
            rs.next()
            val result = rs.getInt(1)
            logger.info("GET_LOCK [$key] connId: ${getConnectionId(conn)}, result: $result")
            return result == 1
        }
    }
    
    private fun releaseLock(conn: Connection, key: String) {
        conn.prepareStatement("SELECT RELEASE_LOCK(?)").use { stmt ->
            stmt.setString(1, key)
            val rs = stmt.executeQuery()
            rs.next()
            val result = rs.getInt(1)
            logger.info("RELEASE_LOCK [$key] connId: ${getConnectionId(conn)}, result: $result")
        }
    }
    
    private fun getConnectionId(conn: Connection): Long {
        conn.prepareStatement("SELECT CONNECTION_ID()").use { stmt ->
            val rs = stmt.executeQuery()
            rs.next()
            return rs.getLong(1)
        }
    }
}