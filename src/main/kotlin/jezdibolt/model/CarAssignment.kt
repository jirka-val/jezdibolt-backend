package jezdibolt.model

import jezdibolt.api.CarAssignmentDto
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.time.LocalDate

class CarAssignment(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<CarAssignment>(CarAssignments)

    var car by Car referencedOn CarAssignments.carId
    var user by User referencedOn CarAssignments.userId
    var shiftType by CarAssignments.shiftType
    var startDate by CarAssignments.startDate
    var endDate by CarAssignments.endDate
    var notes by CarAssignments.notes

    fun close(end: LocalDate) {
        endDate = end
    }
}

fun CarAssignment.toDto(): CarAssignmentDto =
    CarAssignmentDto(
        id = this.id.value,
        carId = this.car.id.value,
        userId = this.user.id.value,
        shiftType = this.shiftType.name,
        startDate = this.startDate.toString(),
        endDate = this.endDate?.toString(),
        notes = this.notes,
        userName = this.user.name,
        carName = "${this.car.brand} ${this.car.model} ${this.car.licensePlate}"
    )
