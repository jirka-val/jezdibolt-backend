package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import jezdibolt.service.BoltImportService
import jezdibolt.service.UserService
import jezdibolt.service.HistoryService
import jezdibolt.util.authUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayOutputStream
import jezdibolt.model.ImportBatches

@Serializable
data class ImportBatchDto(
    val id: Int,
    val filename: String,
    val isoWeek: String,
    val createdAt: String
)

fun Application.importApi(userService: UserService = UserService()) {
    routing {
        route("/import") {

            authenticate("auth-jwt") {

                post("/bolt") {
                    val user = call.authUser() ?: return@post call.respond(HttpStatusCode.Unauthorized)

                    if (!userService.hasPermission(user.id, "EDIT_IMPORT")) {
                        return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nemáte oprávnění nahrávat importy"))
                    }

                    try {
                        val contentType = call.request.contentType()
                        if (!contentType.match(ContentType.MultiPart.FormData)) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to "Expected multipart/form-data, got: $contentType")
                            )
                            return@post
                        }

                        val multipart = call.receiveMultipart()
                        val files = mutableListOf<Pair<ByteArray, String>>()

                        multipart.forEachPart { part ->
                            when (part) {
                                is PartData.FileItem -> {
                                    val filename = part.originalFileName ?: "unknown.csv"
                                    val bytes = withContext(Dispatchers.IO) {
                                        val outputStream = ByteArrayOutputStream()
                                        part.streamProvider().use { input -> input.copyTo(outputStream) }
                                        outputStream.toByteArray()
                                    }
                                    files.add(bytes to filename)

                                    call.application.log.info(
                                        "📦 Import file uploaded by ${user.email} (${user.role}) → $filename"
                                    )
                                }
                                else -> Unit
                            }
                            part.dispose()
                        }

                        if (files.isEmpty()) {
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No files uploaded"))
                            return@post
                        }

                        val service = BoltImportService()
                        val result = service.importFiles(files)

                        HistoryService.log(
                            adminId = user.id,
                            action = "IMPORT_BOLT",
                            entity = "ImportBatch",
                            entityId = null,
                            details = "Uživatel ${user.email} (${user.role}) nahrál ${files.size} soubor(ů): ${files.joinToString { it.second }}"
                        )

                        result.results.forEach { single ->
                            WebSocketConnections.broadcast(
                                """{
                                    "type": "import_completed",
                                    "filename": "${single.filename}",
                                    "batchId": ${single.batchId},
                                    "imported": ${single.imported},
                                    "skipped": ${single.skipped},
                                    "uploadedBy": "${user.email}"
                                }""".trimIndent()
                            )
                        }

                        call.respond(HttpStatusCode.OK, result)

                        call.application.log.info(
                            "✅ ${user.email} (${user.role}) dokončil import ${files.size} souborů."
                        )

                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (e.message ?: "Unknown error"))
                        )
                    }
                }

                get("/list") {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    // Kontrola pouze na právo vidět sekci
                    if (!userService.hasPermission(user.id, "VIEW_IMPORT")) {
                        return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nemáte oprávnění prohlížet importy"))
                    }

                    val imports = transaction {
                        ImportBatches
                            .selectAll()
                            .orderBy(ImportBatches.createdAt to SortOrder.DESC)
                            .map {
                                ImportBatchDto(
                                    id = it[ImportBatches.id].value,
                                    filename = it[ImportBatches.filename],
                                    isoWeek = it[ImportBatches.isoWeek],
                                    createdAt = it[ImportBatches.createdAt].toString()
                                )
                            }
                    }

                    call.application.log.info("📜 ${user.email} (${user.role}) requested import list")
                    call.respond(imports)
                }
            }
        }
    }
}