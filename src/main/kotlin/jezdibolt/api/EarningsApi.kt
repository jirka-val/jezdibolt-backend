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
import jezdibolt.util.authUser
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal

// DTO pro položky v modálu
@Serializable
data class AdjustmentItemDto(
    val id: String? = null,
    val category: String,
    val amount: Double,
    val note: String?
)

// Request body - seznam položek
@Serializable
data class AdjustmentRequest(
    val items: List<AdjustmentItemDto>
)

@Serializable
data class PayRequest(val amount: String)

fun Application.earningsApi() {
    routing {
        route("/earnings") {
            authenticate("auth-jwt") {

                // 🔹 Načtení detailů (položek) pro konkrétní earning a typ
                // GET /earnings/123/adjustments?type=bonus
                get("{id}/adjustments") {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                    val typeParam = call.request.queryParameters["type"]?.uppercase()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing type (bonus/penalty)"))

                    // Mapování frontendu (bonus/penalty) na DB (BONUS/PENALTY)
                    val type = if (typeParam.contains("BONUS")) "BONUS" else "PENALTY"

                    val items = EarningsService.getAdjustments(id, type)
                    call.respond(items)
                }

                // 🔹 Aktualizace BONUSŮ (přijímá seznam)
                put("{id}/bonus") {
                    val user = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid earningId"))

                    val body = call.receive<AdjustmentRequest>()

                    try {
                        // Uložíme a přepočítáme
                        EarningsService.updateAdjustments(id, "BONUS", body.items)

                        HistoryService.log(
                            adminId = user.id,
                            action = "UPDATE_BONUS_LIST",
                            entity = "BoltEarnings",
                            entityId = id,
                            details = "Uživatel ${user.email} upravil bonusy (${body.items.size} položek)"
                        )

                        // Notifikace pro frontend, ať si přenačte data
                        WebSocketConnections.broadcast("""{"type":"earning_updated","id":$id}""")

                        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                    }
                }

                // 🔹 Aktualizace POKUT (přijímá seznam)
                put("{id}/penalty") {
                    val user = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid earningId"))

                    val body = call.receive<AdjustmentRequest>()

                    try {
                        // Uložíme a přepočítáme
                        EarningsService.updateAdjustments(id, "PENALTY", body.items)

                        HistoryService.log(
                            adminId = user.id,
                            action = "UPDATE_PENALTY_LIST",
                            entity = "BoltEarnings",
                            entityId = id,
                            details = "Uživatel ${user.email} upravil pokuty (${body.items.size} položek)"
                        )

                        WebSocketConnections.broadcast("""{"type":"earning_updated","id":$id}""")

                        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                    }
                }

                // ... Zbytek souboru (endpointy /pay, /unpaid/all, /imports/{id}) nech beze změny ...
                // (Zkopíruj sem zbytek původního souboru od `put("{id}/pay")` níže)

                put("{id}/pay") {
                    val user = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid id"))

                    val body = runCatching { call.receiveNullable<PayRequest>() }.getOrNull()
                    val amount = body?.amount?.toBigDecimalOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing or invalid amount"))

                    val result = transaction {
                        val row = BoltEarnings.selectAll().where { BoltEarnings.id eq id }.singleOrNull()
                            ?: return@transaction mapOf("error" to "Earning not found")

                        val currentSettlement = row[BoltEarnings.settlement] ?: BigDecimal.ZERO
                        val currentPartial = row[BoltEarnings.partiallyPaid] ?: BigDecimal.ZERO

                        // Logika platby
                        val isDriverPaying = currentSettlement < BigDecimal.ZERO
                        val payment = amount.abs()

                        val newSettlement = if (isDriverPaying) {
                            currentSettlement + payment
                        } else {
                            currentSettlement - payment
                        }

                        val fullyPaid = newSettlement.abs() < BigDecimal("0.01")

                        if (fullyPaid) {
                            BoltEarnings.update({ BoltEarnings.id eq id }) {
                                it[BoltEarnings.settlement] = BigDecimal.ZERO
                                it[BoltEarnings.paid] = true
                                it[BoltEarnings.paidAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime
                                it[BoltEarnings.partiallyPaid] = BigDecimal.ZERO // Reset partial? Nebo nechat historii? Záleží na logice. Zde resetujeme pro čistotu.
                            }
                            mapOf("status" to "fully paid", "amount" to payment.toPlainString())
                        } else {
                            BoltEarnings.update({ BoltEarnings.id eq id }) {
                                it[BoltEarnings.settlement] = newSettlement
                                it[BoltEarnings.partiallyPaid] = currentPartial + payment
                                it[BoltEarnings.paid] = false
                            }
                            mapOf("status" to "partially paid", "amount" to payment.toPlainString(), "newSettlement" to newSettlement.toPlainString())
                        }
                    }

                    if (result.containsKey("error")) {
                        call.respond(HttpStatusCode.NotFound, result)
                    } else {
                        val type = if (result["status"] == "fully paid") "earning_fully_paid" else "earning_partially_paid"
                        WebSocketConnections.broadcast(
                            """{"type":"$type","id":$id,"amount":"${result["amount"]}"}"""
                        )
                        call.respond(HttpStatusCode.OK, result)
                    }
                }

                // ✅ Endpointy pro čtení (unpaid, imports) zůstávají stejné,
                // protože čtou už předpočítaná data z BoltEarnings tabulky.

                get("/unpaid/all") {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    // ... původní kód ...
                    try {
                        val results = transaction {
                            (BoltEarnings innerJoin UsersSchema)
                                .selectAll()
                                .where { (BoltEarnings.paid eq false) or (BoltEarnings.partiallyPaid greater BigDecimal.ZERO) }
                                .orderBy(BoltEarnings.id, SortOrder.DESC)
                                .map { row -> mapRowToEarningsDto(row) }
                        }
                        call.respond(HttpStatusCode.OK, results)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed"))
                    }
                }

                get("/imports/{id}") {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val batchIdParam = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val paidFilter = call.request.queryParameters["paid"]

                    try {
                        val results = transaction {
                            val query = (BoltEarnings innerJoin UsersSchema)
                                .selectAll()
                                .where { BoltEarnings.batchId eq batchIdParam }

                            when (paidFilter) {
                                "false" -> query.andWhere { (BoltEarnings.paid eq false) or (BoltEarnings.partiallyPaid greater BigDecimal.ZERO) }
                                "true" -> query.andWhere { BoltEarnings.paid eq true }
                            }
                            query.map { row -> mapRowToEarningsDto(row) }
                        }
                        call.respond(HttpStatusCode.OK, results)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed"))
                    }
                }
            }
        }
    }
}

// ... (mapRowToEarningsDto a EarningsDto na konci souboru zůstávají) ...
private fun mapRowToEarningsDto(row: ResultRow): EarningsDto {
    val hoursWorked = row[BoltEarnings.hoursWorked] ?: BigDecimal.ZERO
    val hourlyGross = row[BoltEarnings.hourlyGross] ?: BigDecimal.ZERO
    val cashTaken = row[BoltEarnings.cashTaken] ?: BigDecimal.ZERO
    val bonus = row[BoltEarnings.bonus] ?: BigDecimal.ZERO
    val penalty = row[BoltEarnings.penalty] ?: BigDecimal.ZERO
    val partiallyPaid = row[BoltEarnings.partiallyPaid] ?: BigDecimal.ZERO
    val settlement = row[BoltEarnings.settlement] ?: BigDecimal.ZERO
    val earnings = row[BoltEarnings.earnings] ?: BigDecimal.ZERO

    val appliedRate = PayoutService.getAppliedRate(
        hoursWorked.toDouble(),
        hourlyGross.toDouble()
    )

    return EarningsDto(
        id = row[BoltEarnings.id].value,
        batchId = row[BoltEarnings.batchId].value,
        userName = row[UsersSchema.name],
        email = row[UsersSchema.email],
        hoursWorked = hoursWorked.toDouble(),
        grossPerHour = appliedRate,
        earnings = earnings.toPlainString(),
        cashTaken = cashTaken.toPlainString(),
        bonus = bonus.toPlainString(),
        penalty = penalty.toPlainString(),
        partiallyPaid = partiallyPaid.toPlainString(),
        settlement = settlement.toPlainString(),
        paid = row[BoltEarnings.paid]
    )
}

@Serializable
data class EarningsDto(
    val id: Int,
    val batchId: Int,
    val userName: String,
    val email: String,
    val hoursWorked: Double,
    val grossPerHour: Int,
    val earnings: String,
    val cashTaken: String,
    val bonus: String,
    val penalty: String,
    val partiallyPaid: String,
    val settlement: String,
    val paid: Boolean
)