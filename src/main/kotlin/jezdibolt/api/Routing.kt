package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.content.*
import jezdibolt.service.BoltImportService

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }

        post("/import/bolt") {
            try {
                val multipart = call.receiveMultipart()
                var result: BoltImportService.ImportResult? = null

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        println(" Uploaduju soubor: ${part.originalFileName}") // log
                        val bytes = part.streamProvider().readBytes()
                        val filename = part.originalFileName ?: "unknown.xlsx"

                        val service = BoltImportService()
                        result = service.importXlsx(bytes, filename)
                        println(" Import hotový: $result")
                    }
                    part.dispose()
                }

                if (result == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
                } else {
                    call.respond(result!!)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
            }
        }
    }
}
