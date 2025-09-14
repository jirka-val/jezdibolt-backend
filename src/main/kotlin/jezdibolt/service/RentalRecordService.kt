package jezdibolt.service

import jezdibolt.api.RentalRecordDto
import jezdibolt.model.*
import jezdibolt.repository.RentalRecordRepository
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate

class RentalRecordService(
    private val repo: RentalRecordRepository = RentalRecordRepository()
) {

    fun listRentals(): List<RentalRecordDto> = transaction {
        repo.getAll().map { it.toDto() }
    }

    fun getRental(id: Int): RentalRecordDto? = transaction {
        repo.getById(id)?.toDto()
    }

    fun createRental(
        carId: Int,
        userId: Int,
        startDate: LocalDate,
        endDate: LocalDate?,
        pricePerWeek: BigDecimal?,
        notes: String?,
        contractType: ContractType
    ): RentalRecordDto = transaction {
        RentalRecord.new {
            this.car = Car.findById(carId)!!
            this.user = User.findById(userId)!!
            this.startDate = startDate
            this.endDate = endDate
            this.pricePerWeek = pricePerWeek
            this.notes = notes
            this.contractType = contractType
        }.toDto()
    }


    fun closeRental(id: Int, endDate: LocalDate): RentalRecordDto? = transaction {
        repo.closeRental(id, endDate)?.toDto()
    }

    fun deleteRental(id: Int): Boolean = transaction {
        repo.delete(id)
    }

}
