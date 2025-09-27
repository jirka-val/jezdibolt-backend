package jezdibolt.service

import jezdibolt.model.PayRates
import jezdibolt.model.PayRules
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal

object PayoutService {

    fun calculatePayout(hours: Double, grossPerHour: Double): BigDecimal {
        return transaction {
            val baseRateRow = PayRates
                .selectAll()
                .firstOrNull { row ->
                    val min = row[PayRates.minGross]
                    val max = row[PayRates.maxGross]
                    grossPerHour >= min && (max == null || grossPerHour <= max)
                }

            var rate = baseRateRow?.get(PayRates.rate) ?: 130

            PayRules.selectAll().forEach { rule ->
                when (rule[PayRules.type]) {
                    "min_hours" -> {
                        if (hours <= rule[PayRules.hours].toDouble()) {
                            if (rule[PayRules.mode] == "set") {
                                rate = rule[PayRules.adjustment]
                            }
                        }
                    }
                    "bonus_hours" -> {
                        if (hours >= rule[PayRules.hours].toDouble()) {
                            if (rule[PayRules.mode] == "add") {
                                rate += rule[PayRules.adjustment]
                            }
                        }
                    }
                }
            }

            BigDecimal(rate) * BigDecimal(hours)
        }
    }

    fun getAppliedRate(hours: Double, grossPerHour: Double): Int {
        return transaction {
            val baseRateRow = PayRates
                .selectAll()
                .firstOrNull { row ->
                    val min = row[PayRates.minGross]
                    val max = row[PayRates.maxGross]
                    grossPerHour >= min && (max == null || grossPerHour <= max)
                }

            var rate = baseRateRow?.get(PayRates.rate) ?: 130

            PayRules.selectAll().forEach { rule ->
                when (rule[PayRules.type]) {
                    "min_hours" -> {
                        if (hours <= rule[PayRules.hours].toDouble() && rule[PayRules.mode] == "set") {
                            rate = rule[PayRules.adjustment]
                        }
                    }
                    "bonus_hours" -> {
                        if (hours >= rule[PayRules.hours].toDouble() && rule[PayRules.mode] == "add") {
                            rate += rule[PayRules.adjustment]
                        }
                    }
                }
            }

            rate
        }
    }

    fun seedPayConfig() {
        transaction {
            if (PayRates.selectAll().empty()) {
                PayRates.insert {
                    it[minGross] = 0
                    it[maxGross] = 449
                    it[rate] = 130
                }
                PayRates.insert {
                    it[minGross] = 450
                    it[maxGross] = 549
                    it[rate] = 150
                }
                PayRates.insert {
                    it[minGross] = 550
                    it[maxGross] = 649
                    it[rate] = 170
                }
                PayRates.insert {
                    it[minGross] = 650
                    it[maxGross] = 749
                    it[rate] = 190
                }
                PayRates.insert {
                    it[minGross] = 750
                    it[maxGross] = null
                    it[rate] = 210
                }
            }

            if (PayRules.selectAll().empty()) {
                // pravidlo: <=9 hodin → fix 130/hod
                PayRules.insert {
                    it[type] = "min_hours"
                    it[hours] = 9
                    it[adjustment] = 130
                    it[mode] = "set"
                }
                // pravidlo: >=40 hodin → +10/hod
                PayRules.insert {
                    it[type] = "bonus_hours"
                    it[hours] = 40
                    it[adjustment] = 10
                    it[mode] = "add"
                }
            }
        }
    }
}
