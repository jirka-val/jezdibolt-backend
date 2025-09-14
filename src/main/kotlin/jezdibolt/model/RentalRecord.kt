package jezdibolt.model

import jezdibolt.api.RentalRecordDto
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun RentalRecord.toDto(): RentalRecordDto =
    RentalRecordDto(
        id = this.id.value,
        carId = this.car.id.value,
        userId = this.user.id.value,
        startDate = this.startDate.toString(),
        endDate = this.endDate?.toString(),
        pricePerWeek = this.pricePerWeek?.toPlainString(),
        totalPrice = this.totalPrice?.toPlainString(),
        notes = this.notes,
        userName = this.user.name,
        carName = "${this.car.brand} ${this.car.model} ${this.car.licensePlate}",
        contractType = this.contractType.name
    )

class RentalRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RentalRecord>(RentalRecords)

    var car by Car referencedOn RentalRecords.carId
    var user by User referencedOn RentalRecords.userId
    var startDate by RentalRecords.startDate
    var endDate by RentalRecords.endDate
    var pricePerWeek by RentalRecords.pricePerWeek
    var totalPrice by RentalRecords.totalPrice
    var notes by RentalRecords.notes
    var contractType by RentalRecords.contractType

    fun closeRental(end: LocalDate) {
        endDate = end
        if (contractType == ContractType.WEEKLY) {
            val weeks = ChronoUnit.WEEKS.between(startDate, end).toInt().coerceAtLeast(1)
            totalPrice = pricePerWeek?.multiply(BigDecimal(weeks))
        }
    }
}
