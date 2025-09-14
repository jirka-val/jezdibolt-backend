package jezdibolt.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.date
import java.math.BigDecimal

enum class ContractType {
    HOURLY,   // řidič na hodinovku (výplaty řeší Earnings)
    WEEKLY    // řidič platí fixní týdenní částku
}

object RentalRecords : IntIdTable("rental_records") {
    val carId = reference("car_id", Cars)
    val userId = reference("user_id", UsersSchema)
    val startDate = date("start_date")
    val endDate = date("end_date").nullable()
    val pricePerWeek = decimal("price_per_week", 10, 2).nullable()
    val totalPrice = decimal("total_price", 10, 2).nullable()
    val notes = text("notes").nullable()
    val contractType = enumerationByName("contract_type", 20, ContractType::class)
}
