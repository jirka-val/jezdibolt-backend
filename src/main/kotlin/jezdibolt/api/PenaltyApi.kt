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
import jezdibolt.service.UserService
import jezdibolt.util.authUser
import jezdibolt.model.PenaltyStatus

@kotlinx.serialization.Serializable
data class PenaltyStatusRequest(val status: String)

fun Application.penaltyApi(penaltyService: PenaltyService = PenaltyService(), userService: UserService = UserService()) {
    routing {
        route("/penalties") {
            authenticate("auth-jwt") {

                get {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    if (!userService.hasPermission(user.id, "VIEW_PENALTIES")) {
                        return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nemáte oprávnění prohlížet pokuty"))
                    }

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

                post {
                    val user = call.authUser() ?: return@post call.respond(HttpStatusCode.Unauthorized)

                    if (!userService.hasPermission(user.id, "EDIT_PENALTIES")) {
                        return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nemáte oprávnění vytvářet pokuty"))
                    }

                    val penalty = call.receive<PenaltyDTO>()
                    val created = penaltyService.createPenalty(penalty)

                    HistoryService.log(
                        adminId = user.id,
                        action = "CREATE_PENALTY",
                        entity = "Penalty",
                        entityId = created.id,
                        details = "Uživatel ${user.email} (${user.role}) vytvořil pokutu ID=${created.id} pro userId=${penalty.userId}"
                    )

                    WebSocketConnections.broadcast("""{"type":"penalty_created","id":${created.id}}""")

                    call.application.log.info("🚨 ${user.email} vytvořil pokutu #${created.id}")
                    call.respond(HttpStatusCode.Created, created)
                }

                put("{id}/status") {
                    val user = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    if (!userService.hasPermission(user.id, "EDIT_PENALTIES")) return@put call.respond(HttpStatusCode.Forbidden)

                    val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val body = call.receive<PenaltyStatusRequest>()

                    val newStatus = try {
                        PenaltyStatus.valueOf(body.status)
                    } catch (e: Exception) {
                        return@put call.respond(HttpStatusCode.BadRequest, "Invalid status")
                    }

                    val success = penaltyService.updateStatus(id, newStatus, user.id) // Musíš přidat updateStatus do service

                    if (success) {
                        HistoryService.log(
                            adminId = user.id,
                            action = "UPDATE_PENALTY_STATUS",
                            entity = "Penalty",
                            entityId = id,
                            details = "Stav pokuty změněn na ${newStatus.name}"
                        )
                        WebSocketConnections.broadcast("""{"type":"penalty_updated","id":$id}""")
                        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                patch("{id}/pay") {
                    val user = call.authUser() ?: return@patch call.respond(HttpStatusCode.Unauthorized)

                    if (!userService.hasPermission(user.id, "EDIT_PENALTIES")) {
                        return@patch call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nemáte oprávnění spravovat platby pokut"))
                    }

                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid penalty ID")

                    val resolverId = call.request.queryParameters["resolverId"]?.toIntOrNull()
                    val success = penaltyService.markAsPaid(id, resolverId)

                    if (success) {
                        HistoryService.log(
                            adminId = user.id,
                            action = "PAY_PENALTY",
                            entity = "Penalty",
                            entityId = id,
                            details = "Uživatel ${user.email} (${user.role}) označil pokutu ID=$id jako zaplacenou"
                        )

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