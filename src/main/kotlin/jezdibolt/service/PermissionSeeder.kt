package jezdibolt.service

import jezdibolt.model.PermissionDefinitions
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.transactions.transaction

object PermissionSeeder {
    fun seed() {
        transaction {
            // Pokud je tabulka prázdná, naplníme ji
            if (PermissionDefinitions.selectAll().empty()) {
                val permissions = listOf(
                    Triple("VIEW_DASHBOARD", "Vidět Dashboard", "PAGE"),
                    Triple("VIEW_CARS", "Vidět sekci Auta", "PAGE"),
                    Triple("EDIT_CARS", "Upravovat/Mazat Auta", "ACTION"),
                    Triple("VIEW_USERS", "Vidět sekci Uživatelé", "PAGE"),
                    Triple("EDIT_USERS", "Spravovat práva uživatelů", "ACTION"),
                    Triple("VIEW_FINANCE", "Vidět výplaty a finance", "PAGE"),
                    Triple("VIEW_PENALTIES", "Vidět pokuty", "PAGE"),
                    Triple("EDIT_PENALTIES", "Spravovat pokuty", "ACTION"),
                    Triple("VIEW_RENTALS", "Vidět nájmy", "PAGE"),
                    Triple("EDIT_RENTALS", "Spravovat nájmy", "ACTION")
                )

                PermissionDefinitions.batchInsert(permissions) { (code, label, category) ->
                    this[PermissionDefinitions.code] = code
                    this[PermissionDefinitions.label] = label
                    this[PermissionDefinitions.category] = category
                }
                println(" Permissions seeded successfully.")
            }
        }
    }
}