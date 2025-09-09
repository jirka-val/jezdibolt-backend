package jezdibolt.service

import jezdibolt.model.RentalRecord
import jezdibolt.repository.RentalRecordRepository
import java.math.BigDecimal
import java.time.LocalDate

class RentalRecordService(
    private val repo: RentalRecordRepository = RentalRecordRepository()
) {
    fun listRentals(): List<RentalRecord> = repo.getAll()
    fun getRental(id: Int): RentalRecord? = repo.getById(id)

    fun createRental(
        carId: Int,
        userId: Int,
        startDate: LocalDate,
        endDate: LocalDate?,
        pricePerWeek: BigDecimal,
        notes: String?
    ): RentalRecord = repo.create(carId, userId, startDate, endDate, pricePerWeek, notes)

    fun updateRental(id: Int, builder: RentalRecord.() -> Unit): RentalRecord? = repo.update(id, builder)
    fun deleteRental(id: Int): Boolean = repo.delete(id)
    fun closeRental(id: Int, endDate: LocalDate): RentalRecord? = repo.closeRental(id, endDate)
}
