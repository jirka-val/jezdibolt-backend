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

    // 🆕 create přijímá už rozhashované heslo
    fun create(name: String, email: String, passwordHash: String, contact: String?, role: String, companyId: Int?): UserDTO = transaction {
        val newId = UsersSchema.insertAndGetId {
            it[UsersSchema.name] = name
            it[UsersSchema.email] = email
            it[UsersSchema.passwordHash] = passwordHash // ✅ Ukládáme hash
            it[UsersSchema.contact] = contact
            it[UsersSchema.role] = role
            it[UsersSchema.companyId] = companyId?.let { id -> EntityID(id, Companies) }
        }.value

        // Vrátíme DTO (znovu načtení pro jistotu, nebo zkonstruování)
        UserDTO(newId, name, email, contact, role, companyId)
    }

    // 🆕 update metoda
    fun update(id: Int, name: String, email: String, contact: String?, role: String, companyId: Int?, passwordHash: String?): Boolean = transaction {
        val updated = UsersSchema.update({ UsersSchema.id eq id }) {
            it[UsersSchema.name] = name
            it[UsersSchema.email] = email
            it[UsersSchema.contact] = contact
            it[UsersSchema.role] = role
            it[UsersSchema.companyId] = companyId?.let { id -> EntityID(id, Companies) }
            // Heslo měníme jen pokud je nové zadáno
            if (passwordHash != null) {
                it[UsersSchema.passwordHash] = passwordHash
            }
        }
        updated > 0
    }

    // ... (getAllFiltered a getById zůstávají, jen v nich použij pomocnou metodu mapRowToUser pro čistotu)

    fun getAllFiltered(allowedCities: List<String>, allowedCompanies: List<Int>): List<UserDTO> = transaction {
        val query = (UsersSchema leftJoin Companies).selectAll()

        if (allowedCities.isNotEmpty() || allowedCompanies.isNotEmpty()) {
            query.andWhere {
                val cityCondition = if (allowedCities.isNotEmpty()) (Companies.city inList allowedCities) else Op.FALSE
                val companyCondition = if (allowedCompanies.isNotEmpty()) (Companies.id inList allowedCompanies) else Op.FALSE
                cityCondition or companyCondition
            }
        }
        query.map { mapRowToUser(it) }
    }

    fun getById(id: Int): UserDTO? = transaction {
    UsersSchema.leftJoin(Companies)
        .select(UsersSchema.id eq id) // Removed unnecessary curly braces
        .map { mapRowToUser(it) }
        .singleOrNull()
}

fun getRole(id: Int): String? = transaction {
    UsersSchema.select(UsersSchema.id eq id) // Removed unnecessary curly braces
        .singleOrNull()
        ?.get(UsersSchema.role)
}

    // Pomocná metoda pro mapování
    private fun mapRowToUser(row: ResultRow): UserDTO {
        return UserDTO(
            id = row[UsersSchema.id].value,
            name = row[UsersSchema.name],
            email = row[UsersSchema.email],
            contact = row[UsersSchema.contact],
            role = row[UsersSchema.role],
            companyId = row[UsersSchema.companyId]?.value,
            companyName = row.getOrNull(Companies.name) // Bezpečné čtení z levého joinu
        )
    }
}