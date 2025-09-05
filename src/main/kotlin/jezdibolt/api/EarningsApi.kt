package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.*
import jezdibolt.service.PayoutService
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode

fun Application.earningsApi() {
    routing {
        get("/imports/{id}/earnings") {
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
                            val settlement = payout - cashTaken

                            EarningsDto(
                                id = row[BoltEarnings.id].value,
                                userName = row[UsersSchema.name],
                                email = row[UsersSchema.email],
                                hoursWorked = hoursWorked,
                                grossPerHour = grossPerHour,
                                payout = payout.toPlainString(),
                                cashTaken = cashTaken.toPlainString(),
                                settlement = settlement.toPlainString(),
                                paid = false
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

@Serializable
data class EarningsDto(
    val id: Int,
    val userName: String,
    val email: String,
    val hoursWorked: Int,
    val grossPerHour: Int,
    val payout: String,       // vypočtená mzda
    val cashTaken: String,    // kolik řidič vybral
    val settlement: String,   // finální vyrovnání
    val paid: Boolean
)

