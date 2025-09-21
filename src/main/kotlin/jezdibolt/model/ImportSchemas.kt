package jezdibolt.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import java.math.BigDecimal

object ImportBatches : IntIdTable("import_batches") {
    val filename = varchar("filename", 255)
    val isoWeek = varchar("iso_week", 10)
    val company = varchar("company", 255) // Firma / fleet
    val city = varchar("city", 100).nullable() // Město (Brno, Praha, Ostrava…)
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

    val hoursWorked = integer("hours_worked").default(0)          // ✅ uložené hodiny
    val cashTaken   = decimal("cash_taken_kc",  12, 2).nullable()

    val appliedRate = integer("applied_rate").nullable()          // sazba použitá při importu

    val earnings   = decimal("earnings_kc", 12, 2).nullable()     // nárok (hodiny × sazba + tips)
    val settlement = decimal("settlement_kc", 12, 2).nullable()   // vyrovnání (earnings − hotovost)

    val partiallyPaid = decimal("partially_paid_kc", 12, 2).default(BigDecimal.ZERO)

    val paid = bool("paid").default(false)
    val paidAt = datetime("paid_at").nullable()

    val bonus = decimal("bonus_kc", 12, 2).default(BigDecimal.ZERO)
    val penalty = decimal("penalty_kc", 12, 2).default(BigDecimal.ZERO)

    init {
        index(true, uniqueIdentifier, batchId)
    }
}



