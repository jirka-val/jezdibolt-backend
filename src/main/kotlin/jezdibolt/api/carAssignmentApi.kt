package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.ShiftType
import jezdibolt.service.CarAssignmentService
import jezdibolt.service.HistoryService
import jezdibolt.util.authUser
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class CarAssignmentDto(
    val id: Int,
    val carId: Int,
    val userId: Int,
    val shiftType: String,
    val startDate: String,
    val endDate: String?,
    val notes: String?,
    val userName: String,
    val carName: String
)

@Serializable
data class CarAssignmentRequest(
    val carId: Int,
    val userId: Int,
    val shiftType: String,
    val startDate: String,
    val notes: String? = null
)

@Serializable
data class CloseAssignmentRequest(
    val endDate: String
)

fun Application.carAssignmentApi(service: CarAssignmentService = CarAssignmentService()) {
    routing {
        route("/assignments") {
            authenticate("auth-jwt") {

                // 🔹 všechny záznamy
                get {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val assignments = service.listAssignments()

                    call.application.log.info("📋 ${user.email} (${user.role}) requested assignment list")
                    call.respond(HttpStatusCode.OK, assignments)
                }

                // 🔹 detail
                get("/{id}") {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                    val assignment = service.getAssignment(id)
                    if (assignment == null) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Assignment not found"))
                    } else {
                        call.application.log.info("🔍 ${user.email} viewed assignment $id")
                        call.respond(HttpStatusCode.OK, assignment)
                    }
                }

                // vytvoření
                post {
                    val user = call.authUser() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val req = call.receive<CarAssignmentRequest>()
                    try {
                        val assignment = service.createAssignment(
                            carId = req.carId,
                            userId = req.userId,
                            shiftType = ShiftType.valueOf(req.shiftType),
                            startDate = LocalDate.parse(req.startDate),
                            notes = req.notes
                        )

                        // 🧾 Log
                        HistoryService.log(
                            adminId = user.id,
                            action = "CREATE_ASSIGNMENT",
                            entity = "CarAssignment",
                            entityId = assignment.id,
                            details = "Uživatel ${user.email} (${user.role}) přiřadil řidiče ${req.userId} k autu ${req.carId} (${req.shiftType})"
                        )

                        WebSocketConnections.broadcast(
                            """{"type":"assignment_created","id":${assignment.id},"carId":${assignment.carId},"userId":${assignment.userId}}"""
                        )

                        call.application.log.info("✅ ${user.email} vytvořil assignment #${assignment.id}")
                        call.respond(HttpStatusCode.Created, assignment)

                    } catch (e: IllegalStateException) {
                        call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request"))
                    }
                }

                // ukončení přiřazení
                put("/{id}/close") {
                    val user = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                    val req = call.receive<CloseAssignmentRequest>()
                    val assignment = service.closeAssignment(id, LocalDate.parse(req.endDate))

                    if (assignment == null) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Assignment not found"))
                    } else {
                        HistoryService.log(
                            adminId = user.id,
                            action = "CLOSE_ASSIGNMENT",
                            entity = "CarAssignment",
                            entityId = id,
                            details = "Uživatel ${user.email} (${user.role}) uzavřel přiřazení ID=$id k datu ${req.endDate}"
                        )

                        WebSocketConnections.broadcast(
                            """{"type":"assignment_closed","id":$id,"endDate":"${req.endDate}"}"""
                        )

                        call.respond(HttpStatusCode.OK, assignment)
                    }
                }

                // vyhledání podle SPZ a data
                get("/byCarAndDate") {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val licensePlate = call.request.queryParameters["licensePlate"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing licensePlate"))
                    val dateStr = call.request.queryParameters["date"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing date"))

                    val date = try {
                        LocalDate.parse(dateStr)
                    } catch (e: Exception) {
                        return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid date format, expected YYYY-MM-DD"))
                    }

                    val assignments = service.findAssignmentsByCarAndDate(licensePlate, date)
                    call.application.log.info("🔎 ${user.email} hledal přiřazení auta $licensePlate ($date)")
                    call.respond(HttpStatusCode.OK, assignments)
                }

                // aktivní pro uživatele
                get("/active/user/{userId}") {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val userId = call.parameters["userId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid userId"))

                    val assignments = service.getActiveAssignmentsForUser(userId)
                    call.respond(HttpStatusCode.OK, assignments)
                }

                // aktivní záznamy
                get("/active") {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val assignments = service.listActiveAssignments()
                    call.respond(HttpStatusCode.OK, assignments)
                }

                // smazání
                delete("/{id}") {
                    val user = call.authUser() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                    val deleted = service.deleteAssignment(id)
                    if (deleted) {
                        HistoryService.log(
                            adminId = user.id,
                            action = "DELETE_ASSIGNMENT",
                            entity = "CarAssignment",
                            entityId = id,
                            details = "Uživatel ${user.email} (${user.role}) smazal přiřazení ID=$id"
                        )

                        WebSocketConnections.broadcast("""{"type":"assignment_deleted","id":$id}""")
                        call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Assignment not found"))
                    }
                }
            }
        }
    }
}
