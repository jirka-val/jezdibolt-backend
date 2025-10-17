package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.HistoryLogs
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.historyApi() {
    routing {
        route("/history") {

            // 📜 Vrací historii (s stránkováním)
            get {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
                val offset = ((page - 1).coerceAtLeast(0) * size).toLong()

                try {
                    val total = transaction { HistoryLogs.selectAll().count() }

                    val logs = transaction {
                        HistoryLogs
                            .selectAll()
                            .orderBy(HistoryLogs.timestamp, SortOrder.DESC)
                            .limit(count = size)
                            .offset(start = offset)
                            .map { row ->
                                HistoryDto(
                                    id = row[HistoryLogs.id].value,
                                    timestamp = row[HistoryLogs.timestamp].toString(),
                                    adminId = row[HistoryLogs.adminId].value,
                                    action = row[HistoryLogs.action],
                                    entity = row[HistoryLogs.entity],
                                    entityId = row[HistoryLogs.entityId],
                                    details = row[HistoryLogs.details]
                                )
                            }
                    }

                    call.respond(
                        mapOf(
                            "page" to page,
                            "size" to size,
                            "total" to total,
                            "logs" to logs
                        )
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Nepodařilo se načíst historii")
                    )
                }
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class HistoryDto(
    val id: Int,
    val timestamp: String,
    val adminId: Int,
    val action: String,
    val entity: String,
    val entityId: Int? = null,
    val details: String
)
