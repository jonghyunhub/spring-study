package io.jonghyun.MySQL.pessimisticlock

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class LockMonitoringRepository(
    private val jdbcTemplate: JdbcTemplate
) {

    fun getCurrentLocks(): List<LockInfo> {
        val sql = """
            SELECT
                ENGINE_LOCK_ID,
                LOCK_TYPE,
                LOCK_MODE,
                LOCK_STATUS,
                LOCK_DATA,
                OBJECT_SCHEMA,
                OBJECT_NAME,
                INDEX_NAME
            FROM performance_schema.data_locks
            WHERE OBJECT_SCHEMA = 'coredb'
            ORDER BY ENGINE_LOCK_ID
        """.trimIndent()

        return jdbcTemplate.query(sql) { rs, _ ->
            LockInfo(
                engineLockId = rs.getString("ENGINE_LOCK_ID"),
                lockType = rs.getString("LOCK_TYPE"),
                lockMode = rs.getString("LOCK_MODE"),
                lockStatus = rs.getString("LOCK_STATUS"),
                lockData = rs.getString("LOCK_DATA"),
                objectSchema = rs.getString("OBJECT_SCHEMA"),
                objectName = rs.getString("OBJECT_NAME"),
                indexName = rs.getString("INDEX_NAME")
            )
        }
    }

    fun getInnoDbStatus(): String {
        val sql = "SHOW ENGINE INNODB STATUS"
        return jdbcTemplate.queryForList(sql).toString()
    }
}
