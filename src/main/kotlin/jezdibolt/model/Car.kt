package jezdibolt.model

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.time.LocalDate

class Car(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Car>(Cars)

    var photoUrl by Cars.photoUrl
    var licensePlate by Cars.licensePlate
    var brand by Cars.brand
    var model by Cars.model
    var year by Cars.year
    var fuelType by Cars.fuelType
    var isTaxi by Cars.isTaxi
    var stkValidUntil by Cars.stkValidUntil
    var color by Cars.color
    var status by Cars.status
    var notes by Cars.notes
}


