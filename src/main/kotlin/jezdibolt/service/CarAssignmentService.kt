package jezdibolt.service

import jezdibolt.api.CarAssignmentDto
import jezdibolt.model.*
import jezdibolt.repository.CarAssignmentRepository
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

class CarAssignmentService(
    private val repo: CarAssignmentRepository = CarAssignmentRepository()
) {

    fun listAssignments(): List<CarAssignmentDto> = transaction {
        repo.getAll().map { it.toDto() }
    }

    fun getAssignment(id: Int): CarAssignmentDto? = transaction {
        repo.getById(id)?.toDto()
    }

    fun createAssignment(
        carId: Int,
        userId: Int,
        shiftType: ShiftType,
        startDate: LocalDate,
        notes: String?
    ): CarAssignmentDto = transaction {
        if (repo.hasActiveAssignmentForUser(userId)) {
            throw IllegalStateException("Uživatel už má aktivní přiřazení!")
        }

        repo.create(carId, userId, shiftType, startDate, notes).toDto()
    }

    fun findAssignmentsByCarAndDate(licensePlate: String, date: LocalDate): List<CarAssignmentDto> {
        return transaction {
            val car = Car.find { Cars.licensePlate eq licensePlate }.singleOrNull()
                ?: return@transaction emptyList()

            CarAssignment.find {
                (CarAssignments.carId eq car.id) and
                        (CarAssignments.startDate lessEq date) and
                        ((CarAssignments.endDate.isNull()) or (CarAssignments.endDate greaterEq date))
            }.map { it.toDto() }
        }
    }

    fun listActiveAssignmentsForUser(userId: Int): List<CarAssignmentDto> = transaction {
        repo.getActiveAssignmentsForUser(userId).map { it.toDto() }
    }

    fun closeAssignment(id: Int, endDate: LocalDate): CarAssignmentDto? = transaction {
        repo.closeAssignment(id, endDate)?.toDto()
    }

    fun getActiveAssignmentsForUser(userId: Int): List<CarAssignmentDto> = transaction {
        repo.getActiveAssignmentsForUser(userId).map { it.toDto() }
    }

    fun deleteAssignment(id: Int): Boolean = transaction {
        repo.delete(id)
    }

    fun listActiveAssignments(): List<CarAssignmentDto> = transaction {
        repo.getActiveAssignments().map { it.toDto() }
    }
}
