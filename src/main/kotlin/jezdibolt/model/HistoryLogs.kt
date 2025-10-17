package jezdibolt.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object HistoryLogs : IntIdTable("history_logs") {
    val timestamp = datetime("timestamp").defaultExpression(CurrentDateTime) // ✅ bez závorek
    val adminId = reference("admin_id", UsersSchema) // FK na uživatele
    val action = varchar("action", 100)              // např. CREATE_CAR, IMPORT_BOLT
    val entity = varchar("entity", 100)              // Car, Wage, ImportBatch...
    val entityId = integer("entity_id").nullable()   // konkrétní ID (pokud dává smysl)
    val details = text("details")
}
