package jezdibolt.model

import org.jetbrains.exposed.dao.id.IntIdTable

object UsersSchema : IntIdTable("users") {
    val name = varchar("name", 100)
    val email = varchar("email", 150).uniqueIndex()
    val contact = varchar("contact", 100)
    val role = varchar("role", 50) // např. "driver", "renter"
}
