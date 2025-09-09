package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.Car
import jezdibolt.model.RentalRecord
import jezdibolt.model.UsersSchema
import jezdibolt.service.RentalRecordService
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate

@Serializable
data class RentalRecordDto(
    val id: Int,
    val carId: Int,
    val userId: Int,
    val startDate: String,
    val endDate: String?,
    val pricePerWeek: String,
    val totalPrice: String?,
    val notes: String?
)

fun RentalRecord.toDto() = RentalRecordDto(
    id = id.value,
    carId = car.id.value,
    userId = userId.value,
    startDate = startDate.toString(),
    endDate = endDate?.toString(),
    pricePerWeek = pricePerWeek.toPlainString(),
    totalPrice = totalPrice?.toPlainString(),
    notes = notes
)

@Serializable
data class RentalRecordRequest(
    val carId: Int,
    val userId: Int,
    val startDate: String,
    val endDate: String? = null,
    val pricePerWeek: String,
    val notes: String? = null
)

fun Application.rentalApi(service: RentalRecordService = RentalRecordService()) {
    routing {
        route("/rentals") {

            get {
                val rentals = transaction { service.listRentals().map { it.toDto() } }
                call.respond(HttpStatusCode.OK, rentals)
            }

            get("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                val rental = transaction { service.getRental(id)?.toDto() }
                if (rental == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Rental not found"))
                } else {
                    call.respond(HttpStatusCode.OK, rental)
                }
            }

            post {
                val req = call.receive<RentalRecordRequest>()
                val rental = transaction {
                    service.createRental(
                        carId = req.carId,
                        userId = req.userId,
                        startDate = LocalDate.parse(req.startDate),
                        endDate = req.endDate?.let { LocalDate.parse(it) },
                        pricePerWeek = BigDecimal(req.pricePerWeek),
                        notes = req.notes
                    ).toDto()
                }
                call.respond(HttpStatusCode.Created, rental)
            }


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
                    call.respond(HttpStatusCode.OK, rental.toDto())
                }
            }

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
