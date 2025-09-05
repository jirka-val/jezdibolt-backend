package jezdibolt.repository

import jezdibolt.model.UserDTO
import jezdibolt.model.UsersSchema
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class UserRepository {

    fun getAll(): List<UserDTO> = transaction {
        UsersSchema
            .selectAll()
            .map {
                UserDTO(
                    id = it[UsersSchema.id].value, // EntityID<Int> -> Int
                    name = it[UsersSchema.name],
                    email = it[UsersSchema.email],
                    contact = it[UsersSchema.contact],
                    role = it[UsersSchema.role]
                )
            }
    }

    fun create(user: UserDTO): UserDTO = transaction {
        val newId = UsersSchema.insertAndGetId {
            it[UsersSchema.name] = user.name
            it[UsersSchema.email] = user.email
            it[UsersSchema.contact] = user.contact
            it[UsersSchema.role] = user.role
        }.value

        user.copy(id = newId)
    }
}
