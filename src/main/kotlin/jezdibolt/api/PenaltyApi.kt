package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.PenaltyDTO
import jezdibolt.service.HistoryService
import jezdibolt.service.PenaltyService
import jezdibolt.util.authUser

fun Application.penaltyApi(penaltyService: PenaltyService = PenaltyService()) {
    routing {
        route("/penalties") {
            authenticate("auth-jwt") {

                // 🔹 Načtení pokut (volitelné filtrování podle paid=true/false)
                get {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val paidParam = call.request.queryParameters["paid"]?.lowercase()
                    val paidFilter = when (paidParam) {
                        "true" -> true
                        "false" -> false
                        else -> null
                    }

                    val penalties = penaltyService.getAllPenalties(paidFilter)

                    call.application.log.info("⚖️ ${user.email} (${user.role}) načetl seznam pokut (filter=$paidFilter)")
                    call.respond(HttpStatusCode.OK, penalties)
                }

                // 🔹 Vytvoření nové pokuty
                post {
                    val user = call.authUser() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val penalty = call.receive<PenaltyDTO>()
                    val created = penaltyService.createPenalty(penalty)

                    // 🧾 Záznam do historie
                    HistoryService.log(
                        adminId = user.id,
                        action = "CREATE_PENALTY",
                        entity = "Penalty",
                        entityId = created.id,
                        details = "Uživatel ${user.email} (${user.role}) vytvořil pokutu ID=${created.id} pro userId=${penalty.userId}"
                    )

                    // ✅ Realtime oznámení
                    WebSocketConnections.broadcast("""{"type":"penalty_created","id":${created.id}}""")

                    call.application.log.info("🚨 ${user.email} vytvořil pokutu #${created.id}")
                    call.respond(HttpStatusCode.Created, created)
                }

                // 🔹 Označení pokuty jako zaplacené
                patch("{id}/pay") {
                    val user = call.authUser() ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid penalty ID")

                    val resolverId = call.request.queryParameters["resolverId"]?.toIntOrNull()
                    val success = penaltyService.markAsPaid(id, resolverId)

                    if (success) {
                        // 🧾 Log
                        HistoryService.log(
                            adminId = user.id,
                            action = "PAY_PENALTY",
                            entity = "Penalty",
                            entityId = id,
                            details = "Uživatel ${user.email} (${user.role}) označil pokutu ID=$id jako zaplacenou"
                        )

                        // ✅ Realtime notifikace
                        WebSocketConnections.broadcast("""{"type":"penalty_paid","id":$id}""")

                        call.application.log.info("💸 ${user.email} označil pokutu $id jako zaplacenou")
                        call.respond(HttpStatusCode.OK, mapOf("status" to "paid"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Penalty not found"))
                    }
                }
            }
        }
    }
}
