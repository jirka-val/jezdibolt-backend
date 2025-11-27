package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.service.EarningsService
import jezdibolt.service.RentalRecordService
import kotlinx.serialization.Serializable

@Serializable
data class RentalRecordDto(
    val id: Int,
    val userId: Int,
    val pricePerWeek: String
)

@Serializable
data class SetPriceRequest(val price: String)

fun Application.rentalApi(service: RentalRecordService = RentalRecordService()) {
    routing {
        route("/rentals") {
            authenticate("auth-jwt") {

                put("/user/{userId}/price") {
                    val userId = call.parameters["userId"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest)

                    val req = call.receive<SetPriceRequest>()
                    val price = req.price.toBigDecimalOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid price")

                    service.setPriceForUser(userId, price)

                    EarningsService.recalculateUserEarnings(userId)

                    WebSocketConnections.broadcast("""{"type":"rental_price_updated","userId":$userId}""")

                    call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                }
            }
        }
    }
}