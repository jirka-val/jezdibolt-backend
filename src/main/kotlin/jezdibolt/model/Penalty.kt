package jezdibolt.model

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.date

object Penalties : IntIdTable("penalties") {
    val carId = reference("car_id", Cars)
    val userId = reference("user_id", UsersSchema).nullable()
    val date = date("date")
    val amount = decimal("amount", 10, 2)
    val description = text("description").nullable()
}

@Serializable
data class PenaltyDTO(
    val id: Int? = null,
    val carId: Int,
    val userId: Int? = null,
    val date: String,
    val amount: Double,
    val description: String? = null,
    val carName: String? = null,
    val userName: String? = null
)

