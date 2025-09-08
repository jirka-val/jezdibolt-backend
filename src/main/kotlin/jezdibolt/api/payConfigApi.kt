package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.PayRates
import jezdibolt.model.PayRules
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

// DTOs
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

fun Application.payConfigApi() {
    routing {
        // PAY_RULES
        route("/payrules") {
            get {
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
                call.respond(rules)
            }

            put("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))
                    return@put
                }

                val bodyText = call.receiveText()
                //println("👉 RAW BODY: $bodyText") // LOG

                try {
                    val json = Json.parseToJsonElement(bodyText) as JsonObject
                    //println("👉 Parsed JSON: $json") // LOG

                    transaction {
                        PayRules.update({ PayRules.id eq id }) { row ->
                            json.forEach { (field, jsonElement) ->
                                val value = (jsonElement as JsonPrimitive).content
                                //println("   • updating $field = $value")

                                when (field) {
                                    "hours" -> row[PayRules.hours] = value.toInt()
                                    "adjustment" -> row[PayRules.adjustment] = value.toInt()
                                    "mode" -> row[PayRules.mode] = value
                                    "type" -> row[PayRules.type] = value
                                }
                            }
                        }
                    }
                    call.respond(HttpStatusCode.OK, mapOf("success" to true))
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }
        }

        // PAY_RATES
        route("/payrates") {
            get {
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
                call.respond(rates)
            }

            put("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))
                    return@put
                }

                val bodyText = call.receiveText()
               // println("👉 RAW BODY: $bodyText") // LOG

                try {
                    val json = Json.parseToJsonElement(bodyText) as JsonObject
                    //println("👉 Parsed JSON: $json") // LOG

                    transaction {
                        PayRates.update({ PayRates.id eq id }) { row ->
                            json.forEach { (field, jsonElement) ->
                                val value = (jsonElement as JsonPrimitive).content
                                //println("   • updating $field = $value")

                                when (field) {
                                    "minGross" -> row[PayRates.minGross] = value.toInt()
                                    "maxGross" -> row[PayRates.maxGross] = value.toIntOrNull()
                                    "ratePerHour" -> row[PayRates.rate] = value.toInt()
                                }
                            }
                        }
                    }
                    call.respond(HttpStatusCode.OK, mapOf("success" to true))
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }

        }
    }
}
