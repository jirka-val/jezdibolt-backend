package jezdibolt.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureSecurity() {
    val secret = "super-secret" // TODO: načíst z env/configu
    val issuer = "jezdibolt"
    val audience = "jezdibolt-users"
    val realm = "Access to Jezdibolt"

    install(Authentication) {
        jwt("auth-jwt") {
            this.realm = realm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(secret))
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .build()
            )
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asInt()
                val role = credential.payload.getClaim("role").asString()
                if (userId != null && role != null) JWTPrincipal(credential.payload) else null
            }
        }
    }
}
