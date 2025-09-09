package jezdibolt.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.date

object RentalRecords : IntIdTable("rental_records") {
    val carId = reference("car_id", Cars)                     // které auto
    val userId = reference("user_id", UsersSchema)            // kdo si auto pronajal
    val startDate = date("start_date")                        // od kdy
    val endDate = date("end_date").nullable()                 // dokdy (nullable = běžící pronájem)
    val pricePerWeek = decimal("price_per_week", 10, 2)       // cena za týden
    val totalPrice = decimal("total_price", 10, 2).nullable() // celková cena (počítaná při ukončení)
    val notes = text("notes").nullable()
}
