package jezdibolt.repository

import jezdibolt.model.RentalRecord
import jezdibolt.model.RentalRecords
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal

class RentalRecordRepository {

    fun getByUserId(userId: Int): RentalRecord? = transaction {
        RentalRecord.find { RentalRecords.userId eq userId }.singleOrNull()
    }

    fun upsertPriceForUser(userId: Int, price: BigDecimal) {
        transaction {
            val existing = RentalRecord.find { RentalRecords.userId eq userId }.singleOrNull()

            if (existing != null) {
                existing.pricePerWeek = price
            } else {
                RentalRecord.new {
                    this.user = jezdibolt.model.User.findById(userId)!!
                    this.pricePerWeek = price
                }
            }
        }
    }

    fun deleteForUser(userId: Int) {
        transaction {
            val existing = RentalRecord.find { RentalRecords.userId eq userId }.singleOrNull()
            existing?.delete()
        }
    }
}