package jezdibolt.repository

import jezdibolt.model.Companies
import jezdibolt.model.UserDTO
import jezdibolt.model.UsersSchema
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class UserRepository {

    fun getAll(): List<UserDTO> = transaction {
        UsersSchema.leftJoin(Companies)
            .selectAll()
            .map { mapRowToUser(it) }
    }

    fun create(name: String, email: String, passwordHash: String, contact: String?, role: String, companyId: Int?): UserDTO = transaction {
        val newId = UsersSchema.insertAndGetId {
            it[UsersSchema.name] = name
            it[UsersSchema.email] = email
            it[UsersSchema.passwordHash] = passwordHash
            it[UsersSchema.contact] = contact
            it[UsersSchema.role] = role
            it[UsersSchema.companyId] = companyId?.let { id -> EntityID(id, Companies) }
        }.value

        UserDTO(newId, name, email, contact, role, companyId)
    }

    fun update(id: Int, name: String, email: String, contact: String?, role: String, companyId: Int?, passwordHash: String?): Boolean = transaction {
        val updated = UsersSchema.update({ UsersSchema.id eq id }) {
            it[UsersSchema.name] = name
            it[UsersSchema.email] = email
            it[UsersSchema.contact] = contact
            it[UsersSchema.role] = role
            it[UsersSchema.companyId] = companyId?.let { id -> EntityID(id, Companies) }
            if (passwordHash != null) {
                it[UsersSchema.passwordHash] = passwordHash
            }
        }
        updated > 0
    }

    fun getAllFiltered(
        allowedCities: List<String>,
        allowedCompanies: List<Int>,
        includePrivileged: Boolean
    ): List<UserDTO> = transaction {
        val query = (UsersSchema leftJoin Companies).selectAll()

        query.andWhere {
            val conditions = mutableListOf<Op<Boolean>>()

            if (allowedCities.isNotEmpty()) {
                conditions.add(Companies.city inList allowedCities)
            }
            if (allowedCompanies.isNotEmpty()) {
                conditions.add(Companies.id inList allowedCompanies)
            }

            if (includePrivileged) {
                conditions.add(UsersSchema.role inList listOf("owner", "admin"))
            }

            if (conditions.isNotEmpty()) {
                conditions.reduce { acc, op -> acc or op }
            } else {
                Op.FALSE
            }
        }

        query.map { mapRowToUser(it) }
    }

    // 🛠️ OPRAVENO: Použito .selectAll().where { ... }
    fun getById(id: Int): UserDTO? = transaction {
        UsersSchema.leftJoin(Companies)
            .selectAll()
            .where { UsersSchema.id eq id }
            .map { mapRowToUser(it) }
            .singleOrNull()
    }

    // 🛠️ OPRAVENO: Použito .selectAll().where { ... }
    fun getRole(id: Int): String? = transaction {
        UsersSchema
            .selectAll()
            .where { UsersSchema.id eq id }
            .map { it[UsersSchema.role] }
            .singleOrNull()
    }

    private fun mapRowToUser(row: ResultRow): UserDTO {
        return UserDTO(
            id = row[UsersSchema.id].value,
            name = row[UsersSchema.name],
            email = row[UsersSchema.email],
            contact = row[UsersSchema.contact],
            role = row[UsersSchema.role],
            companyId = row[UsersSchema.companyId]?.value,
            companyName = row.getOrNull(Companies.name)
        )
    }
}