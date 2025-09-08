package jezdibolt.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import java.math.BigDecimal

object ImportBatches : IntIdTable("import_batches") {
    val filename = varchar("filename", 255)
    val isoWeek = varchar("iso_week", 10)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

object BoltEarnings : IntIdTable("bolt_earnings") {
    val userId = reference("user_id", UsersSchema, onDelete = ReferenceOption.CASCADE)
    val batchId = reference("batch_id", ImportBatches, onDelete = ReferenceOption.CASCADE)

    val driverIdentifier = varchar("driver_identifier", 100).nullable()
    val uniqueIdentifier = varchar("unique_identifier", 100).nullable()

    val grossTotal  = decimal("gross_total_kc", 12, 2).nullable()
    val grossApp    = decimal("gross_app_kc",   12, 2).nullable()
    val grossCash   = decimal("gross_cash_kc",  12, 2).nullable()
    val tips        = decimal("tips_kc",        12, 2).nullable()
    val net         = decimal("net_earnings_kc",12, 2).nullable()
    val hourlyGross = decimal("hourly_gross_kc",12, 2).nullable()
    val hourlyNet   = decimal("hourly_net_kc",  12, 2).nullable()

    val cashTaken   = decimal("cash_taken_kc",  12, 2).nullable()

    val appliedRate = integer("applied_rate").nullable()          // sazba použitá při importu
    val payout = decimal("payout_kc", 12, 2).nullable()           // výplata vypočtená při importu


    val paid = bool("paid").default(false)
    val paidAt = datetime("paid_at").nullable()

    val bonus = decimal("bonus_kc", 12, 2).default(BigDecimal.ZERO)
    val penalty = decimal("penalty_kc", 12, 2).default(BigDecimal.ZERO)


    init {
        index(true, uniqueIdentifier, batchId)
    }
}

