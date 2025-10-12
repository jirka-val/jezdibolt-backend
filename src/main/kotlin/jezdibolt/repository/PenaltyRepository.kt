package jezdibolt.repository

import jezdibolt.model.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalDateTime

class PenaltyRepository {

    fun getAll(paidFilter: Boolean? = null): List<PenaltyDTO> = transaction {
        var query = (Penalties innerJoin Cars)
            .join(UsersSchema, JoinType.LEFT, additionalConstraint = { Penalties.userId eq UsersSchema.id })
            .selectAll()

        if (paidFilter != null) {
            query = query.andWhere { Penalties.paid eq paidFilter }
        }

        query.map {
            PenaltyDTO(
                id = it[Penalties.id].value,
                carId = it[Penalties.carId].value,
                userId = it[Penalties.userId]?.value,
                date = it[Penalties.date].toString(),
                amount = it[Penalties.amount].toDouble(),
                description = it[Penalties.description],
                carName = "${it[Cars.brand]} ${it[Cars.model]} (${it[Cars.licensePlate]})",
                userName = it[UsersSchema.name],
                paid = it[Penalties.paid],
                paidAt = it[Penalties.paidAt]?.toString(),
                resolvedBy = it[Penalties.resolvedBy]?.value
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
            it[paid] = penalty.paid
            it[paidAt] = penalty.paidAt?.let { t -> LocalDateTime.parse(t) }
            it[resolvedBy] = penalty.resolvedBy?.let { id -> EntityID(id, UsersSchema) }
        }.value

        penalty.copy(id = newId)
    }

    fun markAsPaid(id: Int, resolverId: Int?): Boolean = transaction {
        val updated = Penalties.update({ Penalties.id eq id }) {
            it[paid] = true
            it[paidAt] = LocalDateTime.now()
            if (resolverId != null) it[resolvedBy] = EntityID(resolverId, UsersSchema)
        }
        updated > 0
    }
}
