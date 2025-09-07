package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.*
import jezdibolt.service.PayoutService
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode

fun Application.earningsApi() {
    routing {
        route("/earnings") {

            @Serializable
            data class BonusRequest(val bonus: String)

            put("{id}/bonus") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid earningId"))

                val body = call.receive<BonusRequest>()
                val bonusValue = body.bonus.toBigDecimalOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing or invalid bonus"))

                transaction {
                    BoltEarnings.update({ BoltEarnings.id eq id }) {
                        it[bonus] = bonusValue
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf("status" to "bonus updated", "bonus" to bonusValue.toPlainString()))
            }

            @Serializable
            data class PenaltyRequest(val penalty: String)

            put("{id}/penalty") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid earningId"))

                val body = call.receive<PenaltyRequest>()
                val penaltyValue = body.penalty.toBigDecimalOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing or invalid penalty"))

                transaction {
                    BoltEarnings.update({ BoltEarnings.id eq id }) {
                        it[penalty] = penaltyValue
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf("status" to "penalty updated", "penalty" to penaltyValue.toPlainString()))
            }

            put("{id}/pay") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid id"))

                transaction {
                    BoltEarnings.update({ BoltEarnings.id eq id }) {
                        it[paid] = true
                        it[paidAt] = CurrentDateTime
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf("status" to "marked as paid"))
            }

            get("/imports/{id}") {
                val batchIdParam = call.parameters["id"]?.toIntOrNull()
                if (batchIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid batchId"))
                    return@get
                }

                try {
                    val results = transaction {
                        (BoltEarnings innerJoin UsersSchema)
                            .selectAll()
                            .where { BoltEarnings.batchId eq batchIdParam }
                            .map { row ->
                                val hourlyGross = row[BoltEarnings.hourlyGross] ?: BigDecimal.ZERO
                                val grossTotal = row[BoltEarnings.grossTotal] ?: BigDecimal.ZERO

                                val hoursWorked = if (hourlyGross > BigDecimal.ZERO) {
                                    grossTotal.divide(hourlyGross, 2, RoundingMode.HALF_UP).toInt()
                                } else {
                                    0
                                }

                                val grossPerHour = hourlyGross.toInt()
                                val payout = PayoutService.calculatePayout(hoursWorked, grossPerHour)
                                val cashTaken = row[BoltEarnings.cashTaken] ?: BigDecimal.ZERO

                                val bonus = row[BoltEarnings.bonus] ?: BigDecimal.ZERO
                                val penalty = row[BoltEarnings.penalty] ?: BigDecimal.ZERO

                                val settlement = payout - cashTaken + bonus - penalty

                                EarningsDto(
                                    id = row[BoltEarnings.id].value,
                                    userName = row[UsersSchema.name],
                                    email = row[UsersSchema.email],
                                    hoursWorked = hoursWorked,
                                    grossPerHour = grossPerHour,
                                    payout = payout.toPlainString(),
                                    cashTaken = cashTaken.toPlainString(),
                                    bonus = bonus.toPlainString(),
                                    penalty = penalty.toPlainString(),
                                    settlement = settlement.toPlainString(),
                                    paid = row[BoltEarnings.paid]
                                )
                            }
                    }

                    call.respond(HttpStatusCode.OK, results)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to fetch earnings data"))
                }
            }
        }
    }
}

@Serializable
data class EarningsDto(
    val id: Int,
    val userName: String,
    val email: String,
    val hoursWorked: Int,
    val grossPerHour: Int,
    val payout: String,
    val cashTaken: String,
    val bonus: String,
    val penalty: String,
    val settlement: String,
    val paid: Boolean
)
