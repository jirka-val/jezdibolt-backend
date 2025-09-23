package jezdibolt.config

import io.ktor.server.application.*
import jezdibolt.model.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.h2.tools.Server

fun Application.configureDatabases() {
    Server.createWebServer("-webAllowOthers", "-webPort", "8082").start()

    val db = Database.connect(
        url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        driver = "org.h2.Driver",
        user = "root",
        password = ""
    )

    transaction(db) {
        SchemaUtils.createMissingTablesAndColumns(
            UsersSchema, ImportBatches, BoltEarnings,
            PayRates, PayRules, Cars, RentalRecords, CarAssignments
        )
        jezdibolt.service.PayoutService.seedPayConfig()
    }
}
