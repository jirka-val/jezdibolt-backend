package jezdibolt.api

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.http.content.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }

        //  statické servírování nahraných souborů
        static("/uploads") {
            files("uploads")
        }
    }

    // registrace modulů
    importApi()
    earningsApi()
    userApi()
    authApi()
    payConfigApi()
    carApi()
    rentalApi()
    carAssignmentApi()
}
