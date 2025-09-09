package jezdibolt.repository

import jezdibolt.model.Car
import jezdibolt.model.RentalRecord
import jezdibolt.model.RentalRecords
import jezdibolt.model.UsersSchema
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
            car = Car.findById(carId) ?: error("Car not found")
            this.userId = EntityID(userId, UsersSchema)
            this.startDate = startDate
            this.endDate = endDate
            this.pricePerWeek = pricePerWeek
            this.notes = notes
        }
    }


    fun update(id: Int, builder: RentalRecord.() -> Unit): RentalRecord? = transaction {
        val rental = RentalRecord.findById(id)
        rental?.apply(builder)
    }

    fun delete(id: Int): Boolean = transaction {
        val rental = RentalRecord.findById(id)
        rental?.delete()
        rental != null
    }

    fun closeRental(id: Int, endDate: LocalDate): RentalRecord? = transaction {
        val rental = RentalRecord.findById(id)
        rental?.apply { closeRental(endDate) }
    }
}
