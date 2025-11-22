package jezdibolt

import io.ktor.server.application.*
import io.ktor.server.websocket.*
import jezdibolt.api.configureRouting
import jezdibolt.api.webSocketApi
import jezdibolt.config.*
import jezdibolt.service.PayoutService
import jezdibolt.service.UserSeeder
import jezdibolt.service.PayoutService.seedPayConfig
import jezdibolt.service.PermissionSeeder
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {

    configureHTTP()
    configureSerialization()
    configureDatabases()
    configureSecurity()
    configureMonitoring()

    // --- Aktivace WebSocketů ---
    install(WebSockets) {
        pingPeriod = 30.seconds
        timeout = 60.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    configureRouting()

    UserSeeder.seedOwner()
    PermissionSeeder.seed()
    PayoutService.seedPayConfig()
}
