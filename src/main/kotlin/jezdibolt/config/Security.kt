package jezdibolt.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.github.cdimascio.dotenv.dotenv
import io.ktor.http.auth.*
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
    val algorithm = Algorithm.HMAC256(secret)

    install(Authentication) {

        /**
         * 🔹 Standardní JWT autentizace (pro REST API)
         * očekává token v hlavičce: Authorization: Bearer <token>
         */
        jwt("auth-jwt") {
            this.realm = realm

            verifier(
                JWT
                    .require(algorithm)
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

        /**
         * 🟢 WebSocket autentizace (token v query parametru ?token=...)
         * používá stejného verifikátora, jen token hledá v URL
         */
        jwt("auth-jwt-query") {
            this.realm = realm

            verifier(
                JWT
                    .require(algorithm)
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .build()
            )

            authHeader { call ->
                call.request.queryParameters["token"]?.let {
                    parseAuthorizationHeader("Bearer $it")
                }
            }

            validate { credential ->
                val userId = credential.payload.getClaim("userId").asInt()
                val role = credential.payload.getClaim("role").asString()
                if (userId != null && role != null) JWTPrincipal(credential.payload) else null
            }
        }
    }

    log.info("✅ JWT security initialized (issuer=$issuer, audience=$audience)")
}
