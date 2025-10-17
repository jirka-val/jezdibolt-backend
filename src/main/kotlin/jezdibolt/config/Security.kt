package jezdibolt.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureSecurity() {
    val dotenv = dotenv {
        ignoreIfMissing = true
    }

    val secret = dotenv["JWT_SECRET"] ?: "default-secret"
    val issuer = dotenv["JWT_ISSUER"] ?: "jezdibolt"
    val audience = dotenv["JWT_AUDIENCE"] ?: "jezdibolt-users"
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
                if (userId != null && role != null) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }

    log.info("✅ JWT security initialized (issuer=$issuer, audience=$audience)")
}
