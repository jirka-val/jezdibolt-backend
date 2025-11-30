package jezdibolt.service

import jezdibolt.api.AdjustmentItemDto
import jezdibolt.model.*
import jezdibolt.repository.RentalRecordRepository
import jezdibolt.repository.UserRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal

object EarningsService {

    private val rentalRepo = RentalRecordRepository()
    private val userRepo = UserRepository()

    /**
     * 🔄 Hlavní metoda pro přepočet výdělků uživatele (např. při změně role).
     * Přepočítá všechny NEZAPLACENÉ výdělky podle aktuální role.
     */
    fun recalculateUserEarnings(userId: Int) {
        transaction {
            val userRole = userRepo.getRole(userId) ?: return@transaction

            // DSL: Najdeme všechny nezaplacené výdělky
            BoltEarnings
                .selectAll()
                .where { (BoltEarnings.userId eq userId) and (BoltEarnings.paid eq false) }
                .forEach { row ->
                    val earningId = row[BoltEarnings.id].value
                    val grossTotal = row[BoltEarnings.grossTotal]

                    if (userRole == "renter") {
                        applyRenterLogic(earningId, userId, grossTotal)
                    } else {
                        applyDriverLogic(earningId)
                    }

                    // Nakonec vždy přepočítáme součty (settlement)
                    recalculateEarnings(earningId)
                }
        }
    }

    /**
     * 🚕 Logika pro DRIVERA:
     * - Smaže automatické poplatky za nájem a servis.
     */
    private fun applyDriverLogic(earningId: Int) {
        EarningAdjustments.deleteWhere {
            (EarningAdjustments.earningId eq earningId) and
                    (EarningAdjustments.type inList listOf("RENTAL_FEE", "SERVICE_FEE"))
        }
    }

    /**
     * 🔑 Logika pro RENTERA:
     * - Vypočítá 4% poplatek z hrubého výdělku.
     * - Najde cenu nájmu pro daného uživatele.
     * - Vytvoří nebo aktualizuje záznamy v earning_adjustments.
     */
    private fun applyRenterLogic(earningId: Int, userId: Int, grossTotal: BigDecimal?) {
        // 1. Poplatek 4% z Hrubého výdělku
        val gross = grossTotal ?: BigDecimal.ZERO
        val serviceFeeAmount = gross.multiply(BigDecimal("0.04")).negate()

        createOrUpdateAdjustment(earningId, "SERVICE_FEE", "Poplatek 4%", serviceFeeAmount)

        // 2. Nájemné
        val rentalPrice = findRentalPriceForUser(userId)

        if (rentalPrice != null && rentalPrice > BigDecimal.ZERO) {
            val rentalFeeAmount = rentalPrice.negate()
            createOrUpdateAdjustment(earningId, "RENTAL_FEE", "Nájem auta", rentalFeeAmount)
        }
    }

    private fun createOrUpdateAdjustment(earningId: Int, type: String, category: String, amount: BigDecimal) {
        // Smažeme starý záznam
        EarningAdjustments.deleteWhere {
            (EarningAdjustments.earningId eq earningId) and (EarningAdjustments.type eq type)
        }
        // Vložíme nový
        EarningAdjustments.insert {
            it[this.earningId] = earningId
            it[this.type] = type
            it[this.category] = category
            it[this.amount] = amount
            it[this.note] = "Automatický výpočet"
        }
    }

    fun updateRate(earningId: Int, newRate: Int) {
        transaction {
            val row = BoltEarnings.selectAll().where { BoltEarnings.id eq earningId }.single()

            val hoursWorked = row[BoltEarnings.hoursWorked] ?: BigDecimal.ZERO
            val tips = row[BoltEarnings.tips] ?: BigDecimal.ZERO

            val newEarnings = (hoursWorked.multiply(BigDecimal(newRate))) + tips

            BoltEarnings.update({ BoltEarnings.id eq earningId }) {
                it[appliedRate] = newRate
                it[earnings] = newEarnings
            }

            recalculateEarnings(earningId)
        }
    }

    private fun findRentalPriceForUser(userId: Int): BigDecimal? {
        // Nová logika: Prostě najdi záznam v "ceníku"
        return RentalRecords
            .selectAll()
            .where { RentalRecords.userId eq userId }
            .map { it[RentalRecords.pricePerWeek] }
            .singleOrNull()
    }

    fun updateAdjustments(earningId: Int, type: String, items: List<AdjustmentItemDto>) {
        transaction {
            EarningAdjustments.deleteWhere {
                (EarningAdjustments.earningId eq earningId) and (EarningAdjustments.type eq type)
            }

            EarningAdjustments.batchInsert(items) { item ->
                this[EarningAdjustments.earningId] = earningId
                this[EarningAdjustments.type] = type
                this[EarningAdjustments.category] = item.category
                this[EarningAdjustments.amount] = item.amount.toBigDecimal()
                this[EarningAdjustments.note] = item.note
            }

            recalculateEarnings(earningId)
        }
    }

    private fun recalculateEarnings(earningId: Int) {
        val adjustments = EarningAdjustments
            .selectAll()
            .where { EarningAdjustments.earningId eq earningId }
            .toList()

        val totalBonus = adjustments.filter { it[EarningAdjustments.type] == "BONUS" }.sumOf { it[EarningAdjustments.amount] }
        val totalPenalty = adjustments.filter { it[EarningAdjustments.type] == "PENALTY" }.sumOf { it[EarningAdjustments.amount] }

        // Renter specifika
        val totalServiceFee = adjustments.filter { it[EarningAdjustments.type] == "SERVICE_FEE" }.sumOf { it[EarningAdjustments.amount] }
        val totalRentalFee = adjustments.filter { it[EarningAdjustments.type] == "RENTAL_FEE" }.sumOf { it[EarningAdjustments.amount] }
        val totalVatDeduction = adjustments.filter { it[EarningAdjustments.type] == "VAT_DEDUCTION" }.sumOf { it[EarningAdjustments.amount] }

        val earningRow = BoltEarnings
            .selectAll()
            .where { BoltEarnings.id eq earningId }
            .single()

        val baseEarnings = earningRow[BoltEarnings.earnings] ?: BigDecimal.ZERO
        val cashTaken = earningRow[BoltEarnings.cashTaken] ?: BigDecimal.ZERO
        val partiallyPaid = earningRow[BoltEarnings.partiallyPaid] ?: BigDecimal.ZERO

        // 🧮 Settlement
        val newSettlement = baseEarnings
            .subtract(cashTaken)
            .add(totalBonus)
            .subtract(totalPenalty)
            .add(totalServiceFee)
            .add(totalRentalFee)
            .add(totalVatDeduction)
            .subtract(partiallyPaid)

        BoltEarnings.update({ BoltEarnings.id eq earningId }) {
            it[bonus] = totalBonus
            it[penalty] = totalPenalty
            it[settlement] = newSettlement

            // 🆕 Uložení renter hodnot
            it[rentalFee] = totalRentalFee
            it[serviceFee] = totalServiceFee
            it[vatDeduction] = totalVatDeduction

            if (newSettlement.abs() > BigDecimal("0.01")) {
                it[paid] = false
            }
        }
    }

    fun getAdjustments(earningId: Int, type: String): List<AdjustmentItemDto> {
        return transaction {
            EarningAdjustments
                .selectAll()
                .where { (EarningAdjustments.earningId eq earningId) and (EarningAdjustments.type eq type) }
                .map {
                    AdjustmentItemDto(
                        id = it[EarningAdjustments.id].value.toString(),
                        category = it[EarningAdjustments.category],
                        amount = it[EarningAdjustments.amount].abs().toDouble(),
                        note = it[EarningAdjustments.note]
                    )
                }
        }
    }
}