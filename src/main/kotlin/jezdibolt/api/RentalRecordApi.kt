package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.ContractType
import jezdibolt.service.RentalRecordService
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate

@Serializable
data class RentalRecordDto(
    val id: Int,
    val carId: Int,
    val userId: Int,
    val startDate: String,
    val endDate: String?,
    val pricePerWeek: String?,
    val totalPrice: String?,
    val notes: String?,
    val userName: String,
    val carName: String,
    val contractType: String
)

@Serializable
data class RentalRecordRequest(
    val carId: Int,
    val userId: Int,
    val startDate: String,
    val endDate: String? = null,
    val pricePerWeek: String? = null,
    val notes: String? = null,
    val contractType: String
)

fun Application.rentalApi(service: RentalRecordService = RentalRecordService()) {
    routing {
        route("/rentals") {

            // všechny záznamy
            get {
                val rentals = service.listRentals()
                call.respond(HttpStatusCode.OK, rentals)
            }

            // detail
            get("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                val rental = service.getRental(id)
                if (rental == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Rental not found"))
                } else {
                    call.respond(HttpStatusCode.OK, rental)
                }
            }

            // vytvoření
            post {
                val req = call.receive<RentalRecordRequest>()
                val rental = service.createRental(
                    carId = req.carId,
                    userId = req.userId,
                    startDate = LocalDate.parse(req.startDate),
                    endDate = req.endDate?.let { LocalDate.parse(it) },
                    pricePerWeek = req.pricePerWeek?.let { BigDecimal(it) },
                    notes = req.notes,
                    contractType = ContractType.valueOf(req.contractType)
                )
                call.respond(HttpStatusCode.Created, rental)
            }

            // uzavření pronájmu
            put("/{id}/close") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                val params = call.receive<Map<String, String>>()
                val endDateStr = params["endDate"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest, mapOf("error" to "Missing endDate")
                )

                val rental = service.closeRental(id, LocalDate.parse(endDateStr))
                if (rental == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Rental not found"))
                } else {
                    call.respond(HttpStatusCode.OK, rental)
                }
            }

            // smazání
            delete("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                val deleted = service.deleteRental(id)
                if (deleted) {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Rental not found"))
                }
            }
        }
    }
}
