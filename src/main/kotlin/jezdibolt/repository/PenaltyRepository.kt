package jezdibolt.repository

import jezdibolt.model.Cars
import jezdibolt.model.Penalties
import jezdibolt.model.PenaltyDTO
import jezdibolt.model.UsersSchema
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

class PenaltyRepository {

    fun getAll(): List<PenaltyDTO> = transaction {
        (Penalties innerJoin Cars leftJoin UsersSchema)
            .selectAll()
            .map {
                PenaltyDTO(
                    id = it[Penalties.id].value,
                    carId = it[Penalties.carId].value,
                    userId = it[Penalties.userId]?.value,
                    date = it[Penalties.date].toString(),
                    amount = it[Penalties.amount].toDouble(),
                    description = it[Penalties.description],
                    carName = "${it[Cars.brand]} ${it[Cars.model]} (${it[Cars.licensePlate]})",
                    userName = it[UsersSchema.name]
                )
            }
    }

    fun create(penalty: PenaltyDTO): PenaltyDTO = transaction {
        val newId = Penalties.insertAndGetId {
            it[carId] = penalty.carId
            it[userId] = penalty.userId
            it[date] = LocalDate.parse(penalty.date)
            it[amount] = penalty.amount.toBigDecimal()
            it[description] = penalty.description
        }.value

        penalty.copy(id = newId)
    }
}
