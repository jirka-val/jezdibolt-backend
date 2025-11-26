package jezdibolt.service

import jezdibolt.model.PermissionDefinitions
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.transactions.transaction

object PermissionSeeder {
    fun seed() {
        transaction {
            // Seznam všech práv v aplikaci
            val permissions = listOf(
                // Dashboard
                Triple("VIEW_DASHBOARD", "Vidět Dashboard", "PAGE"),

                // Import
                Triple("VIEW_IMPORT", "Vidět sekci Import", "PAGE"),
                Triple("EDIT_IMPORT", "Nahrávat/Mazat importy", "ACTION"),

                // Výplaty (Earnings)
                Triple("VIEW_EARNINGS", "Vidět sekci Výplaty", "PAGE"),
                Triple("EDIT_EARNINGS", "Upravovat bonusy/pokuty ve výplatách", "ACTION"),

                // Mzdy (Pay Config)
                Triple("VIEW_PAY_CONFIG", "Vidět sekci Mzdy (Nastavení)", "PAGE"),
                Triple("EDIT_PAY_CONFIG", "Měnit pravidla a sazby mezd", "ACTION"),

                // Auta
                Triple("VIEW_CARS", "Vidět sekci Auta", "PAGE"),
                Triple("EDIT_CARS", "Přidávat/Upravovat auta", "ACTION"),

                // Přiřazení (Assignments)
                Triple("VIEW_ASSIGNMENTS", "Vidět sekci Přiřazení", "PAGE"),
                Triple("EDIT_ASSIGNMENTS", "Měnit přiřazení řidičů", "ACTION"),

                // Pokuty (Penalties)
                Triple("VIEW_PENALTIES", "Vidět sekci Pokuty", "PAGE"),
                Triple("EDIT_PENALTIES", "Přidávat/Mazat pokuty", "ACTION"),

                // Uživatelé
                Triple("VIEW_USERS", "Vidět sekci Uživatelé", "PAGE"),
                Triple("EDIT_USERS", "Spravovat uživatele a práva", "ACTION")
            )

            val existingCodes = PermissionDefinitions.selectAll().map { it[PermissionDefinitions.code] }.toSet()
            val newPermissions = permissions.filter { it.first !in existingCodes }

            if (newPermissions.isNotEmpty()) {
                PermissionDefinitions.batchInsert(newPermissions) { (code, label, category) ->
                    this[PermissionDefinitions.code] = code
                    this[PermissionDefinitions.label] = label
                    this[PermissionDefinitions.category] = category
                }
                println("✅ Permissions seeded: ${newPermissions.size} new entries added.")
            }
        }
    }
}