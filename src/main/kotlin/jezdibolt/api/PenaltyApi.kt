package jezdibolt.api

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import jezdibolt.model.PenaltyDTO
import jezdibolt.service.PenaltyService

fun Application.penaltyApi(penaltyService: PenaltyService = PenaltyService()) {
    routing {
        route("/penalties") {
            get {
                call.respond(penaltyService.getAllPenalties())
            }

            post {
                val penalty = call.receive<PenaltyDTO>()
                val created = penaltyService.createPenalty(penalty)
                call.respond(created)
            }
        }
    }
}
