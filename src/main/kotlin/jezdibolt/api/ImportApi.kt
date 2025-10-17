package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.content.*
import jezdibolt.service.BoltImportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SortOrder
import java.io.ByteArrayOutputStream
import jezdibolt.model.ImportBatches

@Serializable
data class ImportBatchDto(
    val id: Int,
    val filename: String,
    val isoWeek: String,
    val createdAt: String
)

fun Application.importApi() {
    routing {
        route("/import") {

            post("/bolt") {
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
                    val result = service.importFiles(files) // MultiImportResult

                    // ✅ Vezmeme všechny importované soubory a pošleme je klientům
                    result.results.forEach { single ->
                        WebSocketConnections.broadcast(
                            """{
                                "type": "import_completed",
                                "filename": "${single.filename}",
                                "batchId": ${single.batchId},
                                "imported": ${single.imported},
                                "skipped": ${single.skipped}
                            }""".trimIndent()
                        )
                    }

                    call.respond(HttpStatusCode.OK, result)

                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Unknown error"))
                    )
                }
            }

            get("/list") {
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
                call.respond(imports)
            }
        }
    }
}
