package jezdibolt.model

import org.jetbrains.exposed.dao.id.IntIdTable

object UsersSchema : IntIdTable("users") {
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex("USERS_EMAIL_UNIQUE")
    val contact = varchar("contact", 50).nullable()
    val role = varchar("role", 20).default("driver") // default driver
    val passwordHash = varchar("password_hash", 60) // BCrypt hash
}

