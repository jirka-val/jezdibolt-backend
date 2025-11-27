package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.service.DashboardService
import jezdibolt.service.UserService
import jezdibolt.util.authUser

fun Application.dashboardApi(userService: UserService = UserService()) {
    routing {
        route("/dashboard") {
            authenticate("auth-jwt") {
                get("/stats") {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    if (!userService.hasPermission(user.id, "VIEW_DASHBOARD")) {
                        return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nemáte oprávnění vidět dashboard"))
                    }

                    val stats = DashboardService.getStats()
                    if (stats == null) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Žádná data k zobrazení (nahrajte import)"))
                    } else {
                        call.respond(stats)
                    }
                }
            }
        }
    }
}