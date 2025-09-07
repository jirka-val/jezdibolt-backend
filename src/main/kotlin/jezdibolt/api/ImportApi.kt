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
import java.io.ByteArrayOutputStream

fun Application.importApi() {
    routing {
        route("/import") {
            post("/bolt") {
                try {
                    val multipart = call.receiveMultipart()
                    var result: BoltImportService.ImportResult? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                val filename = part.originalFileName ?: "unknown.xlsx"
                                println("Uploaduju soubor: $filename")

                                // Properly read the file content
                                val bytes = withContext(Dispatchers.IO) {
                                    val outputStream = ByteArrayOutputStream()
                                    part.streamProvider().use { input ->
                                        input.copyTo(outputStream)
                                    }
                                    outputStream.toByteArray()
                                }

                                val service = BoltImportService()
                                result = service.importXlsx(bytes, filename)
                                println("Import hotový: $result")
                            }
                            else -> {
                                // Handle other part types if needed
                            }
                        }
                        part.dispose()
                    }

                    when (result) {
                        null -> call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "No file uploaded")
                        )
                        else -> call.respond(HttpStatusCode.OK, result!!)
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Unknown error"))
                    )
                }
            }
        }
    }
}
