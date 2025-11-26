package jezdibolt.service

import jezdibolt.model.PermissionDefinitions
import jezdibolt.model.UserPermissions
import jezdibolt.model.UsersSchema
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction

object UserSeeder {
    fun seedOwner() {
        transaction {
            var ownerId = UsersSchema
                .selectAll()
                .where { UsersSchema.role eq "owner" }
                .map { it[UsersSchema.id] }
                .singleOrNull()

            if (ownerId == null) {
                ownerId = UsersSchema.insertAndGetId {
                    it[name] = "System Owner"
                    it[email] = "owner@example.com"
                    it[contact] = ""
                    it[role] = "owner"
                    it[passwordHash] = PasswordHelper.hash("Default123")
                }
                println("✅ Default owner account created: owner@example.com / Default123")
            } else {
                println("ℹ️ Owner account already exists.")
            }

            if (ownerId != null) {
                val allPermissionCodes = PermissionDefinitions.selectAll().map { it[PermissionDefinitions.code] }

                val currentOwnerPermissions = UserPermissions.selectAll()
                    .where { UserPermissions.userId eq ownerId }
                    .map { it[UserPermissions.permissionCode] }
                    .toSet()

                val missingPermissions = allPermissionCodes.filter { it !in currentOwnerPermissions }

                if (missingPermissions.isNotEmpty()) {
                    UserPermissions.batchInsert(missingPermissions) { code ->
                        this[UserPermissions.userId] = ownerId
                        this[UserPermissions.permissionCode] = code
                    }
                    println("👑 Granted ${missingPermissions.size} new permissions to Owner.")
                }
            }
        }
    }
}