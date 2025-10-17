package jezdibolt.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object UsersSchema : IntIdTable("users") {
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex("USERS_EMAIL_UNIQUE")
    val contact = varchar("contact", 50).nullable()
    val role = varchar("role", 20).default("DRIVER") // DRIVER, ADMIN_COMPANY, ADMIN_GLOBAL
    val passwordHash = varchar("password_hash", 60) // BCrypt hash

    // 🔹 nově přidáno: firma, do které uživatel patří
    val companyId = reference("company_id", Companies, onDelete = ReferenceOption.SET_NULL).nullable()
}
