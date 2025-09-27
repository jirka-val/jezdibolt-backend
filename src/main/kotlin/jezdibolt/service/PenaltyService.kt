package jezdibolt.service

import jezdibolt.model.PenaltyDTO
import jezdibolt.repository.PenaltyRepository

class PenaltyService(
    private val penaltyRepository: PenaltyRepository = PenaltyRepository()
) {
    fun getAllPenalties(): List<PenaltyDTO> = penaltyRepository.getAll()
    fun createPenalty(penalty: PenaltyDTO): PenaltyDTO = penaltyRepository.create(penalty)
}
