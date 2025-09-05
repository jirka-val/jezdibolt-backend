package jezdibolt

import jezdibolt.config.configureHTTP
import jezdibolt.config.configureMonitoring
import jezdibolt.config.configureSecurity
import jezdibolt.api.configureRouting
import jezdibolt.api.userRoutes
import jezdibolt.config.configureSerialization
import jezdibolt.config.configureDatabases
import io.ktor.server.application.*
import io.ktor.server.routing.*
import jezdibolt.api.earningsApi

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureHTTP()
    configureSerialization()
    configureDatabases()
    configureSecurity()
    configureMonitoring()
    configureRouting()
    earningsApi()

    routing {
        userRoutes()
    }
}
