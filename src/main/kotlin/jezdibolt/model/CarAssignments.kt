package jezdibolt.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.date

enum class ShiftType {
    MORNING,   // ranní
    NIGHT,     // noční
    FULL_DAY   // denní (sám na autě)
}

object CarAssignments : IntIdTable("car_assignments") {
    val carId = reference("car_id", Cars)
    val userId = reference("user_id", UsersSchema)
    val shiftType = enumerationByName("shift_type", 20, ShiftType::class)
    val startDate = date("start_date")
    val endDate = date("end_date").nullable()
    val notes = text("notes").nullable()
}
