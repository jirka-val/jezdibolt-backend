package jezdibolt.service

import jezdibolt.model.UsersSchema
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.insert

object UserSeeder {
    fun seedOwner() {
        transaction {
            val existing = UsersSchema
                .selectAll()
                .where { UsersSchema.role eq "owner" }
                .limit(1)
                .singleOrNull()

            if (existing == null) {
                UsersSchema.insert {
                    it[name] = "System Owner"
                    it[email] = "owner@example.com"
                    it[contact] = ""
                    it[role] = "owner"
                    it[passwordHash] = PasswordHelper.hash("Default123")
                }
                println("✅ Default owner account created: owner@example.com / Default123")
            } else {
                println("ℹ️ Owner already exists, skipping seed.")
            }
        }
    }
}
