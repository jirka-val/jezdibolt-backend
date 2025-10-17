package jezdibolt.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime

object Companies : IntIdTable("companies") {
    val name = varchar("name", 255).uniqueIndex() // název firmy (např. Bolt Praha)
    val city = varchar("city", 100).nullable() // město (pro přehled)
    val contactEmail = varchar("contact_email", 255).nullable()
    val phone = varchar("phone", 50).nullable()
}
