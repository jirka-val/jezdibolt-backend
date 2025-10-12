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

            get {
                val paidParam = call.request.queryParameters["paid"]?.lowercase()
                val paidFilter = when (paidParam) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
                call.respond(penaltyService.getAllPenalties(paidFilter))
            }

            post {
                val penalty = call.receive<PenaltyDTO>()
                val created = penaltyService.createPenalty(penalty)
                call.respond(created)
            }

            patch("{id}/pay") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid penalty ID")
                val resolverId = call.request.queryParameters["resolverId"]?.toIntOrNull()
                val success = penaltyService.markAsPaid(id, resolverId)
                if (success) call.respond(HttpStatusCode.OK, "Penalty marked as paid")
                else call.respond(HttpStatusCode.NotFound, "Penalty not found")
            }
        }
    }
}
