package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.ShiftType
import jezdibolt.service.CarAssignmentService
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

            // všechny záznamy
            get {
                val assignments = service.listAssignments()
                call.respond(HttpStatusCode.OK, assignments)
            }

            // detail
            get("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                val assignment = service.getAssignment(id)
                if (assignment == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Assignment not found"))
                } else {
                    call.respond(HttpStatusCode.OK, assignment)
                }
            }

            // vytvoření
            post {
                val req = call.receive<CarAssignmentRequest>()
                try {
                    val assignment = service.createAssignment(
                        carId = req.carId,
                        userId = req.userId,
                        shiftType = ShiftType.valueOf(req.shiftType),
                        startDate = LocalDate.parse(req.startDate),
                        notes = req.notes
                    )

                    // ✅ Po vytvoření pošli websocket oznámení
                    WebSocketConnections.broadcast(
                        """{"type":"assignment_created","id":${assignment.id},"carId":${assignment.carId},"userId":${assignment.userId}}"""
                    )

                    call.respond(HttpStatusCode.Created, assignment)

                } catch (e: IllegalStateException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
                }
            }

            // ukončení přiřazení
            put("/{id}/close") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                val req = call.receive<CloseAssignmentRequest>()
                val assignment = service.closeAssignment(id, LocalDate.parse(req.endDate))

                if (assignment == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Assignment not found"))
                } else {
                    // ✅ Oznámení o uzavření
                    WebSocketConnections.broadcast(
                        """{"type":"assignment_closed","id":$id,"endDate":"${req.endDate}"}"""
                    )

                    call.respond(HttpStatusCode.OK, assignment)
                }
            }

            // vyhledání podle SPZ a data
            get("/byCarAndDate") {
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
                call.respond(HttpStatusCode.OK, assignments)
            }

            // aktivní pro uživatele
            get("/active/user/{userId}") {
                val userId = call.parameters["userId"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid userId"))

                val assignments = service.getActiveAssignmentsForUser(userId)
                call.respond(HttpStatusCode.OK, assignments)
            }

            // aktivní záznamy
            get("/active") {
                val assignments = service.listActiveAssignments()
                call.respond(HttpStatusCode.OK, assignments)
            }

            // smazání
            delete("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                val deleted = service.deleteAssignment(id)
                if (deleted) {
                    // ✅ Realtime oznámení o smazání
                    WebSocketConnections.broadcast("""{"type":"assignment_deleted","id":$id}""")

                    call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Assignment not found"))
                }
            }
        }
    }
}
