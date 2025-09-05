package jezdibolt.service

import jezdibolt.model.UserDTO
import jezdibolt.repository.UserRepository

class UserService(
    private val userRepository: UserRepository = UserRepository()
) {
    fun getAllUsers(): List<UserDTO> = userRepository.getAll()
    fun createUser(user: UserDTO): UserDTO = userRepository.create(user)
}
