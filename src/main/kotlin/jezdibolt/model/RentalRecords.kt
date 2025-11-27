package jezdibolt.model

import org.jetbrains.exposed.dao.id.IntIdTable

object RentalRecords : IntIdTable("rental_records") {
    val userId = reference("user_id", UsersSchema).uniqueIndex() // Unikátní - jeden uživatel = jedna cena
    val pricePerWeek = decimal("price_per_week", 10, 2)
}