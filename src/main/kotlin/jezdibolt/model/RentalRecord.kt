package jezdibolt.model

import jezdibolt.api.RentalRecordDto
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

fun RentalRecord.toDto(): RentalRecordDto =
    RentalRecordDto(
        id = this.id.value,
        userId = this.user.id.value,
        pricePerWeek = this.pricePerWeek.toPlainString()
    )

class RentalRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RentalRecord>(RentalRecords)

    var user by User referencedOn RentalRecords.userId
    var pricePerWeek by RentalRecords.pricePerWeek
}