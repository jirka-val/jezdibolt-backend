package jezdibolt.service

import jezdibolt.model.PayRates
import jezdibolt.model.PayRules
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.transaction

import java.math.BigDecimal

object PayoutService {

    /**
     * Vrátí celkovou odměnu podle aktuálních pravidel.
     */
    fun calculatePayout(hours: Double, grossPerHour: Double): BigDecimal {
        val rate = getAppliedRate(hours, grossPerHour)
        return BigDecimal.valueOf(rate.toLong()) * BigDecimal.valueOf(hours)
    }

    /**
     * Určí hodinovou sazbu podle počtu hodin a hrubého průměru.
     * Bere hodnoty dynamicky z DB.
     */
    fun getAppliedRate(hours: Double, grossPerHour: Double): Int = transaction {
        //  Pravidla — např. pokud někdo odpracoval méně než X hodin
        val rule = PayRules
            .selectAll()
            .firstOrNull { it[PayRules.type] == "under_hours" && hours < it[PayRules.hours] }

        if (rule != null) {
            return@transaction when (rule[PayRules.mode]) {
                "set" -> rule[PayRules.adjustment] // přepíše hodnotu
                "add" -> {
                    val base = findRateByGross(grossPerHour) ?: 130
                    base + rule[PayRules.adjustment]
                }
                else -> 130
            }
        }

        //  Jinak vracíme standardní sazbu podle hrubého průměru
        return@transaction findRateByGross(grossPerHour) ?: 130
    }

    /**
     * Najde sazbu v tabulce PayRates podle hrubého průměru.
     */
    private fun findRateByGross(grossPerHour: Double): Int? = transaction {
        val grossInt = grossPerHour.toInt()

        //  Získáme všechny sazby
        val allRates = PayRates.selectAll().map {
            Triple(it[PayRates.minGross], it[PayRates.maxGross], it[PayRates.rate])
        }

        //  Najdeme první odpovídající záznam podle rozsahu
        val matchingRate = allRates.firstOrNull { (min, max, _) ->
            grossInt >= min && (max == null || grossInt <= max)
        }

        //  Vrátíme nalezenou sazbu
        return@transaction matchingRate?.third
    }

    /**
     * Naplní výchozí hodnoty (jen pokud je DB prázdná).
     */
    fun seedPayConfig() {
        transaction {
            if (PayRates.selectAll().empty()) {
                PayRates.batchInsert(
                    listOf(
                        Triple(0, 449, 140),
                        Triple(450, 559, 160),
                        Triple(560, 659, 180),
                        Triple(660, 759, 200),
                        Triple(760, null, 220)
                    )
                ) { (min, max, rateValue) ->
                    this[PayRates.minGross] = min
                    this[PayRates.maxGross] = max
                    this[PayRates.rate] = rateValue
                }
            }

            if (PayRules.selectAll().empty()) {
                PayRules.insert {
                    it[type] = "under_hours"
                    it[hours] = 35
                    it[adjustment] = 130
                    it[mode] = "set"
                }
            }
        }
    }
}
