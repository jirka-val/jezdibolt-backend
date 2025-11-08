package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.*
import jezdibolt.service.HistoryService
import jezdibolt.service.PayoutService
import jezdibolt.util.authUser
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal

@Serializable
data class PayRequest(val amount: String)

fun Application.earningsApi() {
    routing {
        route("/earnings") {
            authenticate("auth-jwt") {

                // ... [Endpointy PUT /bonus a PUT /penalty zůstávají stejné] ...

                @Serializable
                data class BonusRequest(val bonus: String)

                // 🔹 aktualizace bonusu
                put("{id}/bonus") {
                    val user = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid earningId"))

                    val body = call.receive<BonusRequest>()
                    val bonusValue = body.bonus.toBigDecimalOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing or invalid bonus"))

                    transaction {
                        BoltEarnings.update({ BoltEarnings.id eq id }) {
                            it[BoltEarnings.bonus] = bonusValue
                        }
                    }

                    HistoryService.log(
                        adminId = user.id,
                        action = "UPDATE_BONUS",
                        entity = "BoltEarnings",
                        entityId = id,
                        details = "Uživatel ${user.email} (${user.role}) změnil bonus na $bonusValue"
                    )

                    WebSocketConnections.broadcast(
                        """{"type":"earning_bonus_updated","id":$id,"bonus":"${bonusValue.toPlainString()}"}"""
                    )

                    call.application.log.info("💰 ${user.email} upravil bonus pro výdělek $id na $bonusValue")
                    call.respond(HttpStatusCode.OK, mapOf("status" to "bonus updated", "bonus" to bonusValue.toPlainString()))
                }

                @Serializable
                data class PenaltyRequest(val penalty: String)

                // 🔹 aktualizace pokuty
                put("{id}/penalty") {
                    val user = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid earningId"))

                    val body = call.receive<PenaltyRequest>()
                    val penaltyValue = body.penalty.toBigDecimalOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing or invalid penalty"))

                    transaction {
                        BoltEarnings.update({ BoltEarnings.id eq id }) {
                            it[BoltEarnings.penalty] = penaltyValue
                        }
                    }

                    HistoryService.log(
                        adminId = user.id,
                        action = "UPDATE_PENALTY",
                        entity = "BoltEarnings",
                        entityId = id,
                        details = "Uživatel ${user.email} (${user.role}) upravil pokutu na $penaltyValue"
                    )

                    WebSocketConnections.broadcast(
                        """{"type":"earning_penalty_updated","id":$id,"penalty":"${penaltyValue.toPlainString()}"}"""
                    )

                    call.application.log.info("⚠️ ${user.email} upravil pokutu pro výdělek $id na $penaltyValue")
                    call.respond(HttpStatusCode.OK, mapOf("status" to "penalty updated", "penalty" to penaltyValue.toPlainString()))
                }

                // ... [Endpoint PUT /{id}/pay zůstává stejný] ...

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

                        // 💡 Směr platby určí settlement:
                        //  > 0 → firma platí řidiči
                        //  < 0 → řidič platí firmě
                        val isDriverPaying = currentSettlement < BigDecimal.ZERO
                        val payment = amount.abs() // vždy kladná hodnota vstupu

                        // 🔹 Nový zůstatek po platbě
                        val newSettlement = if (isDriverPaying) {
                            currentSettlement + payment // řidič snižuje svůj dluh (z -600 -> -500)
                        } else {
                            currentSettlement - payment // firma snižuje svůj dluh (z +600 -> +500)
                        }

                        val fullyPaid = newSettlement.abs() < BigDecimal("0.01")

                        if (fullyPaid) {
                            // 🔸 Plně zaplaceno
                            BoltEarnings.update({ BoltEarnings.id eq id }) {
                                it[BoltEarnings.settlement] = BigDecimal.ZERO
                                it[BoltEarnings.paid] = true
                                it[BoltEarnings.paidAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime
                                it[BoltEarnings.partiallyPaid] = BigDecimal.ZERO
                            }
                            mapOf("status" to "fully paid", "amount" to payment.toPlainString())
                        } else {
                            // 🔸 Částečná platba
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

                        HistoryService.log(
                            adminId = user.id,
                            action = "PAY_EARNING",
                            entity = "BoltEarnings",
                            entityId = id,
                            details = "Uživatel ${user.email} (${user.role}) provedl ${result["status"]} platbu ${result["amount"]}"
                        )

                        call.application.log.info("💵 ${user.email} provedl výplatu výdělku $id (${result["status"]})")
                        call.respond(HttpStatusCode.OK, result)
                    }
                }

                // ✅ --- NOVÝ ENDPOINT ---
                // 🔹 Načte VŠECHNY nezaplacené položky napříč všemi importy
                get("/unpaid/all") {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    try {
                        val results = transaction {
                            (BoltEarnings innerJoin UsersSchema)
                                .selectAll()
                                // KLÍČOVÝ FILTR:
                                // paid = false NEBO je tam částečná platba
                                .where { (BoltEarnings.paid eq false) or (BoltEarnings.partiallyPaid greater BigDecimal.ZERO) }
                                .orderBy(BoltEarnings.id, SortOrder.DESC)
                                .map { row ->
                                    mapRowToEarningsDto(row) // Použijeme sdílenou mapovací funkci
                                }
                        }

                        call.application.log.info("📊 ${user.email} načetl VŠECHNY nezaplacené výdělky (${results.size} položek)")
                        call.respond(HttpStatusCode.OK, results)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to fetch all unpaid earnings data"))
                    }
                }

                // ✅ --- VYLEPŠENÝ ENDPOINT ---
                // 🔹 přehled importovaných výdělků podle dávky
                get("/imports/{id}") {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val batchIdParam = call.parameters["id"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid batchId"))

                    // ✅ Čte query parametr ?paid=...
                    val paidFilter = call.request.queryParameters["paid"]

                    try {
                        val results = transaction {
                            val query = (BoltEarnings innerJoin UsersSchema)
                                .selectAll()
                                .where { BoltEarnings.batchId eq batchIdParam }

                            // ✅ Dynamicky přidá filtr podle ?paid=...
                            when (paidFilter) {
                                "false" -> {
                                    query.andWhere { (BoltEarnings.paid eq false) or (BoltEarnings.partiallyPaid greater BigDecimal.ZERO) }
                                }
                                "true" -> {
                                    query.andWhere { BoltEarnings.paid eq true }
                                }
                            }

                            query.map { row ->
                                mapRowToEarningsDto(row) // Použijeme sdílenou mapovací funkci
                            }
                        }

                        call.application.log.info("📊 ${user.email} načetl výdělky z importu #$batchIdParam (filtr: $paidFilter)")
                        call.respond(HttpStatusCode.OK, results)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to fetch earnings data"))
                    }
                }
            }
        }
    }
}

/**
 * ✅ NOVÁ SDÍLENÁ FUNKCE
 * Pomocná funkce pro mapování řádku z DB na EarningsDto.
 * Zabraňuje duplikaci kódu mezi /unpaid/all a /imports/{id}
 */
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
        batchId = row[BoltEarnings.batchId].value, // ✅ PŘIDÁNO batchId
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
    val batchId: Int, // ✅ PŘIDÁNO - Klíčové pro seskupení na frontendu
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