package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.*
import jezdibolt.service.EarningsService
import jezdibolt.service.HistoryService
import jezdibolt.service.PayoutService
import jezdibolt.service.UserService
import jezdibolt.util.authUser
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode

// --- DTOs ---

@Serializable
data class AdjustmentItemDto(
    val id: String? = null,
    val category: String,
    val amount: Double,
    val note: String?
)

@Serializable
data class AdjustmentRequest(
    val items: List<AdjustmentItemDto>
)

@Serializable
data class PayRequest(val amount: String)

// 🟢 AKTUALIZOVANÉ DTO (To, co letí na frontend)
@Serializable
data class EarningsDto(
    val id: Int,
    val userId: Int,        // 🆕 Nutné pro identifikaci usera při změně ceny
    val batchId: Int,
    val userName: String,
    val email: String,
    val role: String,       // 🆕 PŘIDÁNO: Role uživatele (driver/renter) pro filtrování
    val hoursWorked: Double,
    val grossPerHour: Int,  // Naše sazba (podle pravidel)
    val rawHourlyGross: String, // 🆕 Hrubý výdělek/h dle Boltu
    val earnings: String,   // Čistý výdělek (od Boltu)
    val cashTaken: String,
    val bonus: String,
    val penalty: String,
    val partiallyPaid: String,
    val settlement: String, // Konečná částka k výplatě
    val paid: Boolean,

    // 🆕 Nová pole pro Rentery
    val rentalFee: String,
    val serviceFee: String,
    val vatDeduction: String,
    val grossTotal: String,  // Hrubý výdělek

    // 🆕 Procento paliva
    val fuelPercent: String // "15.5" (jako string pro FE)
)

// --- API LOGIKA ---

fun Application.earningsApi(userService: UserService = UserService()) {
    routing {
        route("/earnings") {
            authenticate("auth-jwt") {

                get("{id}/adjustments") {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    if (!userService.hasPermission(user.id, "VIEW_EARNINGS")) {
                        return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                    }
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val typeParam = call.request.queryParameters["type"]?.uppercase() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val type = if (typeParam.contains("BONUS")) "BONUS" else "PENALTY"

                    call.respond(EarningsService.getAdjustments(id, type))
                }

                put("{id}/bonus") {
                    val user = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    if (!userService.hasPermission(user.id, "EDIT_EARNINGS")) return@put call.respond(HttpStatusCode.Forbidden)

                    val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val body = call.receive<AdjustmentRequest>()

                    try {
                        EarningsService.updateAdjustments(id, "BONUS", body.items)
                        HistoryService.log(user.id, "UPDATE_BONUS", "BoltEarnings", id, "Updated bonuses")
                        WebSocketConnections.broadcast("""{"type":"earning_updated","id":$id}""")
                        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }

                put("{id}/penalty") {
                    val user = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    if (!userService.hasPermission(user.id, "EDIT_EARNINGS")) return@put call.respond(HttpStatusCode.Forbidden)

                    val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val body = call.receive<AdjustmentRequest>()

                    try {
                        EarningsService.updateAdjustments(id, "PENALTY", body.items)
                        HistoryService.log(user.id, "UPDATE_PENALTY", "BoltEarnings", id, "Updated penalties")
                        WebSocketConnections.broadcast("""{"type":"earning_updated","id":$id}""")
                        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }

                put("{id}/pay") {
                    val user = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    if (!userService.hasPermission(user.id, "EDIT_EARNINGS")) return@put call.respond(HttpStatusCode.Forbidden)

                    val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val body = runCatching { call.receiveNullable<PayRequest>() }.getOrNull()
                    val amount = body?.amount?.toBigDecimalOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)

                    val result = transaction {
                        val row = BoltEarnings.selectAll().where { BoltEarnings.id eq id }.singleOrNull()
                            ?: return@transaction mapOf("error" to "Not found")

                        val currentSettlement = row[BoltEarnings.settlement] ?: BigDecimal.ZERO
                        val currentPartial = row[BoltEarnings.partiallyPaid] ?: BigDecimal.ZERO

                        val payment = amount.abs()
                        val isDriverPaying = currentSettlement < BigDecimal.ZERO

                        val newSettlement = if (isDriverPaying) currentSettlement + payment else currentSettlement - payment
                        val fullyPaid = newSettlement.abs() < BigDecimal("0.01")

                        if (fullyPaid) {
                            BoltEarnings.update({ BoltEarnings.id eq id }) {
                                it[settlement] = BigDecimal.ZERO
                                it[paid] = true
                                it[paidAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime
                                it[partiallyPaid] = BigDecimal.ZERO
                            }
                            mapOf("status" to "fully paid", "amount" to payment.toPlainString())
                        } else {
                            BoltEarnings.update({ BoltEarnings.id eq id }) {
                                it[settlement] = newSettlement
                                it[partiallyPaid] = currentPartial + payment
                                it[paid] = false
                            }
                            mapOf("status" to "partially paid", "amount" to payment.toPlainString())
                        }
                    }

                    if (result.containsKey("error")) {
                        call.respond(HttpStatusCode.NotFound, result)
                    } else {
                        WebSocketConnections.broadcast("""{"type":"earning_updated","id":$id}""")
                        call.respond(HttpStatusCode.OK, result)
                    }
                }

                // 🔹 SEZNAM PRO RENTALS PAGE I EARNINGS PAGE
                get("/unpaid/all") {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    if (!userService.hasPermission(user.id, "VIEW_EARNINGS")) return@get call.respond(HttpStatusCode.Forbidden)

                    try {
                        val results = transaction {
                            var query = (BoltEarnings innerJoin UsersSchema)
                                .join(Companies, JoinType.LEFT, UsersSchema.companyId, Companies.id)
                                .selectAll()
                                .where { (BoltEarnings.paid eq false) or (BoltEarnings.partiallyPaid greater BigDecimal.ZERO) }

                            // Filtrace podle práv (pokud není owner)
                            if (user.role != "owner") {
                                val allowedCompanies = userService.getAllowedCompanies(user.id)
                                val allowedCities = userService.getAllowedCities(user.id)
                                query = query.andWhere {
                                    val c1 = if (allowedCompanies.isNotEmpty()) (UsersSchema.companyId inList allowedCompanies) else Op.FALSE
                                    val c2 = if (allowedCities.isNotEmpty()) (Companies.city inList allowedCities) else Op.FALSE
                                    c1 or c2
                                }
                            }

                            query.orderBy(BoltEarnings.id, SortOrder.DESC)
                                .map { row -> mapRowToEarningsDto(row) }
                        }
                        call.respond(HttpStatusCode.OK, results)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }

                // Endpoint pro konkrétní import
                get("/imports/{id}") {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    if (!userService.hasPermission(user.id, "VIEW_EARNINGS")) return@get call.respond(HttpStatusCode.Forbidden)

                    val batchId = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)

                    try {
                        val results = transaction {
                            (BoltEarnings innerJoin UsersSchema)
                                .selectAll()
                                .where { BoltEarnings.batchId eq batchId }
                                .map { row -> mapRowToEarningsDto(row) }
                        }
                        call.respond(HttpStatusCode.OK, results)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }
            }
        }
    }
}

// 🛠 MAPPER (Převádí řádek databáze na DTO pro frontend)
private fun mapRowToEarningsDto(row: ResultRow): EarningsDto {
    val earningId = row[BoltEarnings.id].value
    val hoursWorked = row[BoltEarnings.hoursWorked] ?: BigDecimal.ZERO
    val hourlyGross = row[BoltEarnings.hourlyGross] ?: BigDecimal.ZERO
    val cashTaken = row[BoltEarnings.cashTaken] ?: BigDecimal.ZERO
    val bonus = row[BoltEarnings.bonus] ?: BigDecimal.ZERO
    val penalty = row[BoltEarnings.penalty] ?: BigDecimal.ZERO
    val partiallyPaid = row[BoltEarnings.partiallyPaid] ?: BigDecimal.ZERO
    val settlement = row[BoltEarnings.settlement] ?: BigDecimal.ZERO
    val earnings = row[BoltEarnings.earnings] ?: BigDecimal.ZERO

    // 🆕 Načítáme nová pole z databáze
    val rentalFee = row[BoltEarnings.rentalFee] ?: BigDecimal.ZERO
    val serviceFee = row[BoltEarnings.serviceFee] ?: BigDecimal.ZERO
    val vatDeduction = row[BoltEarnings.vatDeduction] ?: BigDecimal.ZERO
    val grossTotal = row[BoltEarnings.grossTotal] ?: BigDecimal.ZERO

    // ⛽ Výpočet paliva
    val fuelAmount = EarningAdjustments
        .selectAll()
        .where { (EarningAdjustments.earningId eq earningId) and (EarningAdjustments.category eq "fuel") }
        .sumOf { it[EarningAdjustments.amount] }

    val fuelPercent = if (grossTotal.compareTo(BigDecimal.ZERO) != 0) {
        fuelAmount.divide(grossTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
    } else {
        BigDecimal.ZERO
    }

    val appliedRate = PayoutService.getAppliedRate(
        hoursWorked.toDouble(),
        hourlyGross.toDouble()
    )

    return EarningsDto(
        id = earningId,
        userId = row[BoltEarnings.userId].value,
        batchId = row[BoltEarnings.batchId].value,
        userName = row[UsersSchema.name],
        email = row[UsersSchema.email],
        role = row[UsersSchema.role],
        hoursWorked = hoursWorked.toDouble(),
        grossPerHour = appliedRate, // Naše kalkulovaná sazba
        rawHourlyGross = hourlyGross.toPlainString(), // 🆕 Bolt sazba
        earnings = earnings.toPlainString(),
        cashTaken = cashTaken.toPlainString(),
        bonus = bonus.toPlainString(),
        penalty = penalty.toPlainString(),
        partiallyPaid = partiallyPaid.toPlainString(),
        settlement = settlement.toPlainString(),
        paid = row[BoltEarnings.paid],

        rentalFee = rentalFee.toPlainString(),
        serviceFee = serviceFee.toPlainString(),
        vatDeduction = vatDeduction.toPlainString(),
        grossTotal = grossTotal.toPlainString(),

        fuelPercent = fuelPercent.setScale(1, RoundingMode.HALF_UP).toPlainString() // 🆕
    )
}