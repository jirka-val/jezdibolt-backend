package jezdibolt.service

import jezdibolt.api.AdjustmentItemDto
import jezdibolt.model.BoltEarnings
import jezdibolt.model.EarningAdjustments
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal

object EarningsService {

    /**
     * Uloží seznam položek (bonusů nebo pokut) a přepočítá celkový výdělek.
     */
    fun updateAdjustments(earningId: Int, type: String, items: List<AdjustmentItemDto>) {
        transaction {
            // 1. Smažeme staré položky tohoto typu pro tento earning (režim "nahradit vše")
            // Tím vyřešíme i mazání položek, které uživatel odebral na frontendu
            EarningAdjustments.deleteWhere {
                (EarningAdjustments.earningId eq earningId) and (EarningAdjustments.type eq type)
            }

            // 2. Vložíme nové položky
            EarningAdjustments.batchInsert(items) { item ->
                this[EarningAdjustments.earningId] = earningId
                this[EarningAdjustments.type] = type
                this[EarningAdjustments.category] = item.category
                this[EarningAdjustments.amount] = item.amount.toBigDecimal()
                this[EarningAdjustments.note] = item.note
            }

            // 3. PŘEPOČET HLAVNÍ TABULKY
            recalculateEarnings(earningId)
        }
    }

    /**
     * Vytáhne všechny adjustmenty, sečte je a aktualizuje hlavní záznam BoltEarnings.
     */
    private fun recalculateEarnings(earningId: Int) {
        // Načteme všechny adjustmenty pro tento earning
        val adjustments = EarningAdjustments
            .selectAll()
            .where { EarningAdjustments.earningId eq earningId }
            .toList()

        val totalBonus = adjustments
            .filter { it[EarningAdjustments.type] == "BONUS" }
            .sumOf { it[EarningAdjustments.amount] }

        val totalPenalty = adjustments
            .filter { it[EarningAdjustments.type] == "PENALTY" }
            .sumOf { it[EarningAdjustments.amount] }

        // Načteme aktuální earning řádek pro základní hodnoty
        val earningRow = BoltEarnings
            .selectAll()
            .where { BoltEarnings.id eq earningId }
            .single()

        val baseEarnings = earningRow[BoltEarnings.earnings] ?: BigDecimal.ZERO
        val cashTaken = earningRow[BoltEarnings.cashTaken] ?: BigDecimal.ZERO

        // 🧮 VZOREC: Settlement = (Výdělek - Hotovost) + Bonusy - Pokuty
        // Pozn: Pokud uživatel už něco zaplatil (partiallyPaid), to se odečte až při platbě,
        // settlement ukazuje "kolik zbývá doplatit/vyrovnat".

        // Pokud settlement má odrážet "celkový dluh před zaplacením", vzorec je:
        // (Earnings - Cash) + Bonus - Penalty.
        // Pokud máš logiku, že settlement se snižuje platbami, musíme být opatrní.
        // Většinou je lepší držet "TotalDebt" a "PaidAmount" zvlášť.
        // Ale pro zachování tvé stávající logiky settlementu:

        val newSettlement = baseEarnings
            .subtract(cashTaken)
            .add(totalBonus)
            .subtract(totalPenalty)
            // Pokud uživatel už něco zaplatil, musíme to zohlednit?
            // V tvém modelu 'settlement' funguje jako "current balance".
            // Pokud recalculujeme, vracíme se k "teoretickému dluhu".
            // Musíme odečíst to, co už bylo zaplaceno (partiallyPaid).
            .subtract(earningRow[BoltEarnings.partiallyPaid] ?: BigDecimal.ZERO)

        // Update hlavního záznamu
        BoltEarnings.update({ BoltEarnings.id eq earningId }) {
            it[bonus] = totalBonus
            it[penalty] = totalPenalty
            it[settlement] = newSettlement

            // Pokud se změnou částky dostaneme na 0 (nebo blízko), můžeme označit jako paid?
            // Raději neautomatizovat 'paid = true' zde, nechat to na ručním potvrzení nebo payment endpointu.
            // Ale pokud se settlement změní na nenulový, měli bychom asi shodit 'paid' na false.
            if (newSettlement.abs() > BigDecimal("0.01")) {
                it[paid] = false
            }
        }
    }

    /**
     * Načte položky pro zobrazení v modálu
     */
    fun getAdjustments(earningId: Int, type: String): List<AdjustmentItemDto> {
        return transaction {
            EarningAdjustments
                .selectAll()
                .where { (EarningAdjustments.earningId eq earningId) and (EarningAdjustments.type eq type) }
                .map {
                    AdjustmentItemDto(
                        id = it[EarningAdjustments.id].value.toString(),
                        category = it[EarningAdjustments.category],
                        amount = it[EarningAdjustments.amount].toDouble(),
                        note = it[EarningAdjustments.note]
                    )
                }
        }
    }
}