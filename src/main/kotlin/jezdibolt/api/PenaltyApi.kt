package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.PenaltyDTO
import jezdibolt.service.PenaltyService

fun Application.penaltyApi(penaltyService: PenaltyService = PenaltyService()) {
    routing {
        route("/penalties") {

            //  Načtení pokut (volitelné filtrování podle paid=true/false)
            get {
                val paidParam = call.request.queryParameters["paid"]?.lowercase()
                val paidFilter = when (paidParam) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
                call.respond(penaltyService.getAllPenalties(paidFilter))
            }

            //  Vytvoření nové pokuty
            post {
                val penalty = call.receive<PenaltyDTO>()
                val created = penaltyService.createPenalty(penalty)

                // ✅ Notifikace přes WebSocket — nová pokuta vytvořena
                WebSocketConnections.broadcast("""{"type":"penalty_created","id":${created.id}}""")

                call.respond(HttpStatusCode.Created, created)
            }

            //  Označení pokuty jako zaplacené
            patch("{id}/pay") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid penalty ID")

                val resolverId = call.request.queryParameters["resolverId"]?.toIntOrNull()
                val success = penaltyService.markAsPaid(id, resolverId)

                if (success) {
                    // ✅ Notifikace přes WebSocket — pokuta zaplacena
                    WebSocketConnections.broadcast("""{"type":"penalty_paid","id":$id}""")

                    call.respond(HttpStatusCode.OK, mapOf("status" to "paid"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Penalty not found"))
                }
            }
        }
    }
}
