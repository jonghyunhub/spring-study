package io.jonghyun.MySQL.pessimisticlock

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LockMonitoringService(
    private val lockMonitoringRepository: LockMonitoringRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getCurrentLocks(): List<LockInfo> {
        return lockMonitoringRepository.getCurrentLocks()
    }

    fun printCurrentLocks(description: String = "") {
        val locks = getCurrentLocks()

        logger.info("========================================")
        if (description.isNotEmpty()) {
            logger.info("Lock Status: $description")
        }
        logger.info("Total Locks: ${locks.size}")
        logger.info("========================================")

        locks.forEach { lock ->
            logger.info("""
                |Lock Info:
                |  - Lock ID: ${lock.engineLockId}
                |  - Lock Type: ${lock.lockType}
                |  - Lock Mode: ${lock.lockMode}
                |  - Lock Status: ${lock.lockStatus}
                |  - Table: ${lock.objectSchema}.${lock.objectName}
                |  - Index: ${lock.indexName ?: "N/A"}
                |  - Lock Data: ${lock.lockData ?: "N/A"}
            """.trimMargin())
            logger.info("----------------------------------------")
        }

        if (locks.isEmpty()) {
            logger.info("No locks found")
        }

        logger.info("========================================\n")
    }
}
