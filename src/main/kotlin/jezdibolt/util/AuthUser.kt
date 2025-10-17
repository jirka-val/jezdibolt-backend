package jezdibolt.util

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

data class AuthUser(
    val id: Int,
    val email: String,
    val role: String,
    val companyId: Int?
)

fun ApplicationCall.authUser(): AuthUser? {
    val principal = this.principal<JWTPrincipal>() ?: return null
    return AuthUser(
        id = principal.payload.getClaim("userId").asInt(),
        email = principal.payload.getClaim("email").asString(),
        role = principal.payload.getClaim("role").asString(),
        companyId = principal.payload.getClaim("companyId").asInt()
    )
}

