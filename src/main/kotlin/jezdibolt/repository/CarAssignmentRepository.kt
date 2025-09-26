package jezdibolt.repository

import jezdibolt.model.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

class CarAssignmentRepository {

    fun getAll(): List<CarAssignment> = transaction {
        CarAssignment.all().toList()
    }

    fun getById(id: Int): CarAssignment? = transaction {
        CarAssignment.findById(id)
    }

    fun create(
        carId: Int,
        userId: Int,
        shiftType: ShiftType,
        startDate: LocalDate,
        notes: String?
    ): CarAssignment = transaction {
        CarAssignment.new {
            this.car = Car.findById(carId)!!
            this.user = User.findById(userId)!!
            this.shiftType = shiftType
            this.startDate = startDate
            this.notes = notes
        }
    }

    fun closeAssignment(id: Int, endDate: LocalDate): CarAssignment? = transaction {
        val assignment = CarAssignment.findById(id) ?: return@transaction null
        assignment.close(endDate)
        assignment
    }

    fun update(id: Int, builder: CarAssignment.() -> Unit): CarAssignment? = transaction {
        val assignment = CarAssignment.findById(id) ?: return@transaction null
        assignment.apply(builder)
        assignment
    }

    fun delete(id: Int): Boolean = transaction {
        val assignment = CarAssignment.findById(id) ?: return@transaction false
        assignment.delete()
        true
    }

    fun getActiveAssignmentsForUser(userId: Int): List<CarAssignment> = transaction {
        CarAssignment.find {
            (CarAssignments.userId eq userId) and CarAssignments.endDate.isNull()
        }.toList()
    }

    fun hasActiveAssignmentForUser(userId: Int): Boolean = transaction {
        CarAssignment.find {
            (CarAssignments.userId eq userId) and (CarAssignments.endDate.isNull())
        }.empty().not()
    }

    fun getActiveAssignments(): List<CarAssignment> = transaction {
        CarAssignment.find { CarAssignments.endDate.isNull() }.toList()
    }

    fun getAssignmentsForCar(carId: Int): List<CarAssignment> = transaction {
        CarAssignment.find { CarAssignments.carId eq carId }.toList()
    }

    fun getAssignmentsForUser(userId: Int): List<CarAssignment> = transaction {
        CarAssignment.find { CarAssignments.userId eq userId }.toList()
    }
}
