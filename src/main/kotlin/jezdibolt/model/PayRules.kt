package jezdibolt.model

import org.jetbrains.exposed.dao.id.IntIdTable

object PayRules : IntIdTable("pay_rules") {
    val type = varchar("type", 50) // "min_hours" nebo "bonus_hours"
    val hours = integer("hours")   // hranice hodin
    val adjustment = integer("adjustment") // změna (např. 130 nebo +10)
    val mode = varchar("mode", 10) // "set" = nastav novou sazbu, "add" = přičti
}
