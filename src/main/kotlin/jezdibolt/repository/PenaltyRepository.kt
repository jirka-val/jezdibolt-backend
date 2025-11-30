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
            if (paidFilter == true) {
                query = query.andWhere { Penalties.status eq PenaltyStatus.PAID }
            } else {
                query = query.andWhere { Penalties.status eq PenaltyStatus.PENDING }
            }
        }

        query.orderBy(Penalties.date, SortOrder.DESC)
            .map {
                PenaltyDTO(
                    id = it[Penalties.id].value,
                    carId = it[Penalties.carId].value,
                    userId = it[Penalties.userId]?.value,
                    date = it[Penalties.date].toString(),
                    amount = it[Penalties.amount].toDouble(),
                    description = it[Penalties.description],
                    carName = "${it[Cars.brand]} ${it[Cars.model]} (${it[Cars.licensePlate]})",
                    userName = it[UsersSchema.name],
                    paid = it[Penalties.status] == PenaltyStatus.PAID,
                    status = it[Penalties.status].name,
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
            it[status] = PenaltyStatus.PENDING // Default
            it[paid] = false
        }.value

        penalty.copy(id = newId, status = "PENDING")
    }

    fun updateStatus(id: Int, newStatus: PenaltyStatus, resolverId: Int?): Boolean = transaction {
        val updated = Penalties.update({ Penalties.id eq id }) {
            it[status] = newStatus

            if (newStatus == PenaltyStatus.PAID) {
                it[paid] = true
                it[paidAt] = LocalDateTime.now()
                if (resolverId != null) it[resolvedBy] = EntityID(resolverId, UsersSchema)
            } else {
                it[paid] = false
                it[paidAt] = null
                it[resolvedBy] = null
            }
        }
        updated > 0
    }

    fun markAsPaid(id: Int, resolverId: Int?): Boolean {
        return updateStatus(id, PenaltyStatus.PAID, resolverId)
    }
}