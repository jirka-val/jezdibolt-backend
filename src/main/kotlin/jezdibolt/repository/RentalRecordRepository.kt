package jezdibolt.repository

import jezdibolt.model.RentalRecord
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate

class RentalRecordRepository {

    fun getAll(): List<RentalRecord> = transaction {
        RentalRecord.all().toList()
    }

    fun getById(id: Int): RentalRecord? = transaction {
        RentalRecord.findById(id)
    }

    fun create(
        carId: Int,
        userId: Int,
        startDate: LocalDate,
        endDate: LocalDate?,
        pricePerWeek: BigDecimal,
        notes: String?
    ): RentalRecord = transaction {
        RentalRecord.new {
            this.car = jezdibolt.model.Car.findById(carId)!!
            this.user = jezdibolt.model.User.findById(userId)!!
            this.startDate = startDate
            this.endDate = endDate
            this.pricePerWeek = pricePerWeek
            this.notes = notes
        }
    }

    fun closeRental(id: Int, endDate: LocalDate): RentalRecord? = transaction {
        val record = RentalRecord.findById(id) ?: return@transaction null
        record.closeRental(endDate)
        record
    }

    fun update(id: Int, builder: RentalRecord.() -> Unit): RentalRecord? = transaction {
        val record = RentalRecord.findById(id) ?: return@transaction null
        record.apply(builder)
        record
    }

    fun delete(id: Int): Boolean = transaction {
        val record = RentalRecord.findById(id) ?: return@transaction false
        record.delete()
        true
    }
}
