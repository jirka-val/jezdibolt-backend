package jezdibolt.service

import jezdibolt.model.PenaltyDTO
import jezdibolt.repository.PenaltyRepository

class PenaltyService(
    private val penaltyRepository: PenaltyRepository = PenaltyRepository()
) {
    fun getAllPenalties(paidFilter: Boolean? = null): List<PenaltyDTO> =
        penaltyRepository.getAll(paidFilter)

    fun createPenalty(penalty: PenaltyDTO): PenaltyDTO =
        penaltyRepository.create(penalty)

    fun markAsPaid(id: Int, resolverId: Int?): Boolean =
        penaltyRepository.markAsPaid(id, resolverId)
}
