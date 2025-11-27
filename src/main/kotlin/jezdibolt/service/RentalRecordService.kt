package jezdibolt.service

import jezdibolt.repository.RentalRecordRepository
import java.math.BigDecimal

class RentalRecordService(
    private val repo: RentalRecordRepository = RentalRecordRepository()
) {
    fun setPriceForUser(userId: Int, price: BigDecimal) {
        repo.upsertPriceForUser(userId, price)
    }

    fun getPriceForUser(userId: Int): BigDecimal? {
        return repo.getByUserId(userId)?.pricePerWeek
    }
}