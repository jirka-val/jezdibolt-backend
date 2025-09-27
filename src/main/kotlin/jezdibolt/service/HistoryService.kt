package jezdibolt.service

import jezdibolt.model.HistoryLogs
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

object HistoryService {
    fun log(
        adminId: Int,
        action: String,
        entity: String,
        entityId: Int? = null,
        details: String = ""
    ) {
        transaction {
            HistoryLogs.insert {
                it[timestamp] = LocalDateTime.now()
                it[HistoryLogs.adminId] = adminId
                it[HistoryLogs.action] = action
                it[HistoryLogs.entity] = entity
                it[HistoryLogs.entityId] = entityId
                it[HistoryLogs.details] = details
            }
        }
    }
}
