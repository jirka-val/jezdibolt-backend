package jezdibolt.model

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class RentalRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RentalRecord>(RentalRecords)

    var car by Car referencedOn RentalRecords.carId
    var userId by RentalRecords.userId
    var startDate by RentalRecords.startDate
    var endDate by RentalRecords.endDate
    var pricePerWeek by RentalRecords.pricePerWeek
    var totalPrice by RentalRecords.totalPrice
    var notes by RentalRecords.notes


    fun closeRental(end: LocalDate) {
        endDate = end
        val weeks = ChronoUnit.WEEKS.between(startDate, end).toInt().coerceAtLeast(1)
        totalPrice = pricePerWeek.multiply(BigDecimal(weeks))
    }
}
