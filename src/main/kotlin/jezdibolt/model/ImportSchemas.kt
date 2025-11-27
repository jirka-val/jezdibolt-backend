package jezdibolt.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import java.math.BigDecimal

object ImportBatches : IntIdTable("import_batches") {
    val filename = varchar("filename", 255)
    val isoWeek = varchar("iso_week", 10)
    val company = varchar("company", 255)
    val city = varchar("city", 100).nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

object BoltEarnings : IntIdTable("bolt_earnings") {
    val userId = reference("user_id", UsersSchema, onDelete = ReferenceOption.CASCADE)
    val batchId = reference("batch_id", ImportBatches, onDelete = ReferenceOption.CASCADE)

    val driverIdentifier = varchar("driver_identifier", 100).nullable()
    val uniqueIdentifier = varchar("unique_identifier", 100).nullable()

    val grossTotal  = decimal("gross_total_kc", 12, 2).nullable()
    val tips        = decimal("tips_kc",        12, 2).nullable()
    val hourlyGross = decimal("hourly_gross_kc",12, 2).nullable()

    val hoursWorked = decimal("hours_worked", 10, 2)
    val cashTaken   = decimal("cash_taken_kc",  12, 2).nullable()

    val appliedRate = integer("applied_rate").nullable()

    val earnings   = decimal("earnings_kc", 12, 2).nullable()
    val settlement = decimal("settlement_kc", 12, 2).nullable()

    val partiallyPaid = decimal("partially_paid_kc", 12, 2).default(BigDecimal.ZERO)

    val paid = bool("paid").default(false)
    val paidAt = datetime("paid_at").nullable()

    val bonus = decimal("bonus_kc", 12, 2).default(BigDecimal.ZERO)
    val penalty = decimal("penalty_kc", 12, 2).default(BigDecimal.ZERO)

    // 🆕 NOVÉ SLOUPCE PRO RENTERY (Cache pro rychlé zobrazení)
    val rentalFee = decimal("rental_fee_kc", 12, 2).default(BigDecimal.ZERO)
    val serviceFee = decimal("service_fee_kc", 12, 2).default(BigDecimal.ZERO)
    val vatDeduction = decimal("vat_deduction_kc", 12, 2).default(BigDecimal.ZERO)

    init {
        index(true, uniqueIdentifier, batchId)
    }
}

object EarningAdjustments : IntIdTable("earning_adjustments") {
    val earningId = reference("earning_id", BoltEarnings, onDelete = ReferenceOption.CASCADE)
    val type = varchar("type", 20)
    val category = varchar("category", 50)
    val amount = decimal("amount", 12, 2)
    val note = text("note").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}