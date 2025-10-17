package jezdibolt.api

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.UsersSchema
import jezdibolt.service.PasswordHelper
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

fun Application.authApi() {
    routing {
        route("/auth") {

            @Serializable
            data class LoginRequest(val email: String, val password: String)

            @Serializable
            data class LoginResponse(
                val id: Int,
                val name: String,
                val role: String,
                val token: String
            )

            post("/login") {
                val body = runCatching { call.receive<LoginRequest>() }.getOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))

                val user = transaction {
                    UsersSchema
                        .selectAll()
                        .where { UsersSchema.email eq body.email.lowercase() }
                        .singleOrNull()
                }

                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                    return@post
                }

                val passwordHash = user[UsersSchema.passwordHash]
                if (!PasswordHelper.verify(body.password, passwordHash)) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                    return@post
                }

                val userId = user[UsersSchema.id].value
                val name = user[UsersSchema.name]
                val role = user[UsersSchema.role]
                val email = user[UsersSchema.email]

                // stejné hodnoty jako v configureSecurity()
                val jwtSecret = System.getenv("JWT_SECRET") ?: "default-secret"
                val jwtIssuer = System.getenv("JWT_ISSUER")
                val jwtAudience = System.getenv("JWT_AUDIENCE")

                val expirationTime = 1000L * 60L * 60L * 10L // 10 hodin

                val token = JWT.create()
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .withClaim("userId", userId)
                    .withClaim("email", email)
                    .withClaim("role", role)
                    .withIssuedAt(Date())
                    .withExpiresAt(Date(System.currentTimeMillis() + expirationTime))
                    .sign(Algorithm.HMAC256(jwtSecret))

                // ✅ realtime event – přihlášení
                WebSocketConnections.broadcast(
                    """{"type":"user_logged_in","userId":$userId,"role":"$role","name":"$name"}"""
                )

                // ✅ možnost pozdějšího logování do historie (pokud přidáš HistoryLogger)
                // HistoryLogger.logAction(userId, "login", "user", userId, "Uživatel se přihlásil")

                call.respond(
                    LoginResponse(
                        id = userId,
                        name = name,
                        role = role,
                        token = token
                    )
                )
            }
        }
    }
}
