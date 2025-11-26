package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.PayRates
import jezdibolt.model.PayRules
import jezdibolt.service.HistoryService
import jezdibolt.service.UserService // ✅ Import UserService
import jezdibolt.util.authUser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class PayRuleDto(
    val id: Int,
    val type: String,
    val hours: Int,
    val adjustment: Int,
    val mode: String
)

@Serializable
data class PayRateDto(
    val id: Int,
    val minGross: Int,
    val maxGross: Int? = null,
    val ratePerHour: Int
)

fun Application.payConfigApi(userService: UserService = UserService()) {
    routing {
        authenticate("auth-jwt") {

            route("/payrules") {

                get {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    if (!userService.hasPermission(user.id, "VIEW_PAY_CONFIG")) {
                        return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nemáte oprávnění prohlížet nastavení mezd"))
                    }

                    val rules = transaction {
                        PayRules.selectAll().map {
                            PayRuleDto(
                                id = it[PayRules.id].value,
                                type = it[PayRules.type],
                                hours = it[PayRules.hours],
                                adjustment = it[PayRules.adjustment],
                                mode = it[PayRules.mode]
                            )
                        }
                    }
                    call.application.log.info("⚙️ ${user.email} (${user.role}) requested pay rules")
                    call.respond(rules)
                }

                put("{id}") {
                    val user = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)

                    if (!userService.hasPermission(user.id, "EDIT_PAY_CONFIG")) {
                        return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nemáte oprávnění upravovat pravidla mezd"))
                    }

                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                    val bodyText = call.receiveText()

                    try {
                        val json = Json.parseToJsonElement(bodyText) as JsonObject

                        transaction {
                            PayRules.update({ PayRules.id eq id }) { row ->
                                json.forEach { (field, jsonElement) ->
                                    val value = (jsonElement as JsonPrimitive).content
                                    when (field) {
                                        "hours" -> row[PayRules.hours] = value.toInt()
                                        "adjustment" -> row[PayRules.adjustment] = value.toInt()
                                        "mode" -> row[PayRules.mode] = value
                                        "type" -> row[PayRules.type] = value
                                    }
                                }
                            }
                        }

                        HistoryService.log(
                            adminId = user.id,
                            action = "UPDATE_PAY_RULE",
                            entity = "PayRules",
                            entityId = id,
                            details = "Uživatel ${user.email} (${user.role}) upravil PayRule ID=$id (${json.toString()})"
                        )

                        WebSocketConnections.broadcast("""{"type":"payrule_updated","id":$id}""")

                        call.application.log.info("🔧 ${user.email} aktualizoval PayRule $id")
                        call.respond(HttpStatusCode.OK, mapOf("success" to true))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                    }
                }
            }

            route("/payrates") {

                get {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    if (!userService.hasPermission(user.id, "VIEW_PAY_CONFIG")) {
                        return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nemáte oprávnění prohlížet nastavení mezd"))
                    }

                    val rates = transaction {
                        PayRates.selectAll().map {
                            PayRateDto(
                                id = it[PayRates.id].value,
                                minGross = it[PayRates.minGross],
                                maxGross = it[PayRates.maxGross],
                                ratePerHour = it[PayRates.rate]
                            )
                        }
                    }
                    call.application.log.info("📊 ${user.email} (${user.role}) requested pay rates")
                    call.respond(rates)
                }

                put("{id}") {
                    val user = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)

                    if (!userService.hasPermission(user.id, "EDIT_PAY_CONFIG")) {
                        return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nemáte oprávnění upravovat sazby mezd"))
                    }

                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                    val bodyText = call.receiveText()

                    try {
                        val json = Json.parseToJsonElement(bodyText) as JsonObject

                        transaction {
                            PayRates.update({ PayRates.id eq id }) { row ->
                                json.forEach { (field, jsonElement) ->
                                    val value = (jsonElement as JsonPrimitive).content
                                    when (field) {
                                        "minGross" -> row[PayRates.minGross] = value.toInt()
                                        "maxGross" -> row[PayRates.maxGross] = value.toIntOrNull()
                                        "ratePerHour" -> row[PayRates.rate] = value.toInt()
                                    }
                                }
                            }
                        }

                        HistoryService.log(
                            adminId = user.id,
                            action = "UPDATE_PAY_RATE",
                            entity = "PayRates",
                            entityId = id,
                            details = "Uživatel ${user.email} (${user.role}) upravil PayRate ID=$id (${json.toString()})"
                        )

                        WebSocketConnections.broadcast("""{"type":"payrate_updated","id":$id}""")

                        call.application.log.info("💼 ${user.email} aktualizoval PayRate $id")
                        call.respond(HttpStatusCode.OK, mapOf("success" to true))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                    }
                }
            }
        }
    }
}