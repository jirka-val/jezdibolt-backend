package jezdibolt

import jezdibolt.api.configureRouting
import jezdibolt.api.userApi
import io.ktor.server.application.*
import jezdibolt.api.earningsApi
import jezdibolt.api.importApi
import jezdibolt.config.*
import jezdibolt.service.UserSeeder

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

    //earningsApi()
    //userApi()
    //importApi()

    UserSeeder.seedOwner()
}
