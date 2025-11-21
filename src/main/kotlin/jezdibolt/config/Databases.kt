package jezdibolt.config

import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*
import jezdibolt.model.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.h2.tools.Server

fun Application.configureDatabases() {
    val dotenv = dotenv {
        ignoreIfMissing = true
    }

    val appEnv = dotenv["APP_ENV"] ?: System.getenv("APP_ENV") ?: "dev"
    val isDev = appEnv == "dev"

    // 🔹 automaticky použij H2, pokud běží testy
    val isTest = System.getProperty("org.gradle.test.worker") != null

    val dbUrl = dotenv["DATABASE_URL"] ?: System.getenv("DATABASE_URL")
    val dbUser = dotenv["DATABASE_USER"] ?: System.getenv("DATABASE_USER")
    val dbPassword = dotenv["DATABASE_PASSWORD"] ?: System.getenv("DATABASE_PASSWORD")

    val db = when {
        isTest || isDev -> {
            Server.createWebServer("-webAllowOthers", "-webPort", "8082").start()
            Database.connect(
                url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
                driver = "org.h2.Driver",
                user = "root",
                password = ""
            )
        }
        else -> {
            Database.connect(
                url = dbUrl ?: error("DATABASE_URL not set"),
                driver = "org.postgresql.Driver",
                user = dbUser ?: error("DATABASE_USER not set"),
                password = dbPassword ?: error("DATABASE_PASSWORD not set")
            )
        }
    }

    if (isDev || isTest) {
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(
                UsersSchema,
                ImportBatches,
                BoltEarnings,
                PayRates,
                PayRules,
                Cars,
                RentalRecords,
                CarAssignments,
                Penalties,
                HistoryLogs,
                EarningAdjustments,
                Companies,
                PermissionDefinitions,
                UserPermissions,
                UserCompanyAccess,
                UserCityAccess
            )
        }
    } else {
        val flyway = Flyway.configure()
            .dataSource(dbUrl, dbUser, dbPassword)
            .load()
        flyway.migrate()
    }

    log.info("✅ Database initialized for environment: $appEnv (isTest=$isTest)")
}
