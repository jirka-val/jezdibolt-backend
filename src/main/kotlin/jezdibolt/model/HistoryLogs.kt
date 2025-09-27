package jezdibolt.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime

object HistoryLogs : IntIdTable("history_logs") {
    val timestamp = datetime("timestamp")
    val adminId = reference("admin_id", UsersSchema) // FK na uživatele
    val action = varchar("action", 100)              // např. CREATE_CAR, UPDATE_WAGE
    val entity = varchar("entity", 100)              // Car, Wage, Penalty...
    val entityId = integer("entity_id").nullable()   // konkrétní ID (pokud dává smysl)
    val details = text("details")
}
