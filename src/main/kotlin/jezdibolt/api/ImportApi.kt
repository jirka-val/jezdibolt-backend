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

// 🔹 DTO pro výsledek importu (serializovatelný)
@Serializable
data class ImportResultDto(
    val imported: Int,
    val skipped: Int,
    val batchId: Int
)

// 🔹 odpověď pro POST /import/bolt
@Serializable
data class ImportResponse(
    val importResult: ImportResultDto,
    val filename: String
)

// 🔹 odpověď pro GET /import/list
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
                    var result: BoltImportService.ImportResult? = null
                    var uploadedFilename: String? = null
                    var conflictError: String? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                val filename = part.originalFileName ?: "unknown.xlsx"
                                uploadedFilename = filename

                                // ✅ kontrola duplicit
                                val exists = transaction {
                                    ImportBatches
                                        .selectAll()
                                        .any { it[ImportBatches.filename] == filename }
                                }
                                if (exists) {
                                    conflictError = "Soubor '$filename' už byl importován."
                                } else {
                                    val bytes = withContext(Dispatchers.IO) {
                                        val outputStream = ByteArrayOutputStream()
                                        part.streamProvider().use { input -> input.copyTo(outputStream) }
                                        outputStream.toByteArray()
                                    }

                                    val service = BoltImportService()
                                    result = service.importXlsx(bytes, filename)
                                }
                            }
                            else -> Unit
                        }
                        part.dispose()
                    }

                    // ✅ rozhodnutí až po forEachPart
                    if (conflictError != null) {
                        call.respond(HttpStatusCode.Conflict, mapOf("error" to conflictError))
                    } else if (result == null || uploadedFilename == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "No file uploaded")
                        )
                    } else {
                        val dto = ImportResultDto(
                            imported = result!!.imported,
                            skipped = result!!.skipped,
                            batchId = result!!.batchId
                        )
                        call.respond(HttpStatusCode.OK, ImportResponse(dto, uploadedFilename!!))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Unknown error"))
                    )
                }
            }

            // 🔹 nový endpoint pro list importů
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
