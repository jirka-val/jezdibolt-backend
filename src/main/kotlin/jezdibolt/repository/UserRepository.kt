package jezdibolt.repository

import jezdibolt.model.Companies
import jezdibolt.model.UserDTO
import jezdibolt.model.UsersSchema
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class UserRepository {

    fun getAll(): List<UserDTO> = transaction {
        UsersSchema
            .selectAll()
            .map {
                UserDTO(
                    id = it[UsersSchema.id].value,
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

    fun getAllFiltered(allowedCities: List<String>, allowedCompanies: List<Int>): List<UserDTO> = transaction {
        val query = (UsersSchema leftJoin Companies)
            .selectAll()

        if (allowedCities.isNotEmpty() || allowedCompanies.isNotEmpty()) {
            query.andWhere {
                val cityCondition = if (allowedCities.isNotEmpty()) (Companies.city inList allowedCities) else Op.FALSE
                val companyCondition = if (allowedCompanies.isNotEmpty()) (Companies.id inList allowedCompanies) else Op.FALSE

                cityCondition or companyCondition
            }
        }

        query.map {
            UserDTO(
                id = it[UsersSchema.id].value,
                name = it[UsersSchema.name],
                email = it[UsersSchema.email],
                contact = it[UsersSchema.contact],
                role = it[UsersSchema.role],
                companyId = it[UsersSchema.companyId]?.value,
                companyName = it[Companies.name]
            )
        }
    }

    fun getById(id: Int): UserDTO? = transaction {
        UsersSchema.leftJoin(Companies)
            .select(UsersSchema.id eq id)
            .map {
                UserDTO(
                    id = it[UsersSchema.id].value,
                    name = it[UsersSchema.name],
                    email = it[UsersSchema.email],
                    contact = it[UsersSchema.contact],
                    role = it[UsersSchema.role],
                    companyId = it[UsersSchema.companyId]?.value,
                    companyName = it[Companies.name]
                )
            }
            .singleOrNull()
    }

    fun getRole(id: Int): String? = transaction {
        UsersSchema
            .select(UsersSchema.id eq id)
            .singleOrNull()
            ?.get(UsersSchema.role)
    }
}