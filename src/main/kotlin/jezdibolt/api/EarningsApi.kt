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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal

@Serializable
data class PayRequest(val amount: String)

fun Application.earningsApi() {
    routing {
        route("/earnings") {
            authenticate("auth-jwt") {

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

                // 🔹 vyplacení (částkové nebo plné)
                put("{id}/pay") {
                    val user = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid id"))

                    val body = runCatching { call.receiveNullable<PayRequest>() }.getOrNull()
                    val amount = body?.amount?.toBigDecimalOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing or invalid amount"))

                    val result: Map<String, String> = transaction {
                        val row = BoltEarnings.selectAll().where { BoltEarnings.id eq id }.singleOrNull()
                            ?: return@transaction mapOf("error" to "Earning not found")

                        val earnings = row[BoltEarnings.earnings] ?: BigDecimal.ZERO
                        val cashTaken = row[BoltEarnings.cashTaken] ?: BigDecimal.ZERO
                        val bonus = row[BoltEarnings.bonus] ?: BigDecimal.ZERO
                        val penalty = row[BoltEarnings.penalty] ?: BigDecimal.ZERO
                        val alreadyPaid = row[BoltEarnings.partiallyPaid] ?: BigDecimal.ZERO

                        val settlement = earnings - cashTaken + bonus - penalty - alreadyPaid

                        if (amount >= settlement) {
                            BoltEarnings.update({ BoltEarnings.id eq id }) {
                                it[BoltEarnings.paid] = true
                                it[BoltEarnings.paidAt] = CurrentDateTime
                                it[BoltEarnings.partiallyPaid] = earnings - cashTaken + bonus - penalty
                            }
                            mapOf("status" to "fully paid", "amount" to amount.toPlainString())
                        } else {
                            BoltEarnings.update({ BoltEarnings.id eq id }) {
                                it[BoltEarnings.partiallyPaid] = alreadyPaid + amount
                            }
                            mapOf("status" to "partially paid", "amount" to amount.toPlainString())
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
                            details = "Uživatel ${user.email} (${user.role}) vyplatil ${result["status"]} částku ${result["amount"]}"
                        )

                        call.application.log.info("💵 ${user.email} provedl výplatu výdělku $id (${result["status"]})")
                        call.respond(HttpStatusCode.OK, result)
                    }
                }

                // 🔹 přehled importovaných výdělků podle dávky
                get("/imports/{id}") {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val batchIdParam = call.parameters["id"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid batchId"))

                    try {
                        val results = transaction {
                            (BoltEarnings innerJoin UsersSchema)
                                .selectAll()
                                .where { BoltEarnings.batchId eq batchIdParam }
                                .map { row ->
                                    val grossTotal = row[BoltEarnings.grossTotal] ?: BigDecimal.ZERO
                                    val hourlyGross = row[BoltEarnings.hourlyGross] ?: BigDecimal.ZERO
                                    val hoursWorked = row[BoltEarnings.hoursWorked] ?: BigDecimal.ZERO
                                    val cashTaken = row[BoltEarnings.cashTaken] ?: BigDecimal.ZERO
                                    val bonus = row[BoltEarnings.bonus] ?: BigDecimal.ZERO
                                    val penalty = row[BoltEarnings.penalty] ?: BigDecimal.ZERO
                                    val partiallyPaid = row[BoltEarnings.partiallyPaid] ?: BigDecimal.ZERO

                                    val appliedRate = PayoutService.getAppliedRate(
                                        hoursWorked.toDouble(),
                                        hourlyGross.toDouble()
                                    )

                                    val earnings = row[BoltEarnings.earnings] ?: BigDecimal.ZERO
                                    val settlement = earnings - cashTaken + bonus - penalty - partiallyPaid

                                    EarningsDto(
                                        id = row[BoltEarnings.id].value,
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
                        }
                        call.application.log.info("📊 ${user.email} načetl výdělky z importu #$batchIdParam")
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

@Serializable
data class EarningsDto(
    val id: Int,
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
