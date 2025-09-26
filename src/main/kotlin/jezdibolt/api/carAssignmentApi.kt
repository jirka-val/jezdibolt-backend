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
                    call.respond(HttpStatusCode.OK, assignment)
                }
            }

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
                    call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Assignment not found"))
                }
            }
        }
    }
}
