package jezdibolt.util

import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.http.*
import io.ktor.server.auth.*

suspend fun ApplicationCall.requireRole(vararg allowed: String, block: suspend () -> Unit) {
    val principal = this.principal<JWTPrincipal>()
    val role = principal?.payload?.getClaim("role")?.asString()

    if (role != null && allowed.contains(role)) {
        block()
    } else {
        respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
    }
}
