package jezdibolt.model

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object Penalties : IntIdTable("penalties") {
    val carId = reference("car_id", Cars, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", UsersSchema).nullable()
    val date = date("date")
    val amount = decimal("amount", 10, 2)
    val description = text("description").nullable()

    val paid = bool("paid").default(false)
    val paidAt = datetime("paid_at").nullable()
    val resolvedBy = reference("resolved_by", UsersSchema).nullable() // kdo označil jako zaplacené
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
    val userName: String? = null,

    val paid: Boolean = false,
    val paidAt: String? = null,
    val resolvedBy: Int? = null,
    val resolvedByName: String? = null
)
