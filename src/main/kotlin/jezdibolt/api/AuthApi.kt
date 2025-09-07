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
            data class LoginResponse(val token: String, val role: String)

            post("/login") {
                val body = call.receive<LoginRequest>()

                val user = transaction {
                    UsersSchema.selectAll()
                        .where { UsersSchema.email eq body.email }
                        .singleOrNull()
                }

                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                    return@post
                }

                val passwordHash = user[UsersSchema.passwordHash]
                val role = user[UsersSchema.role]

                if (!PasswordHelper.verify(body.password, passwordHash)) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                    return@post
                }

                // Vytvoření JWT tokenu
                val jwtSecret = "secret"
                val jwtIssuer = "jezdibolt-app"
                val jwtAudience = "jezdibolt-users"

                val token = JWT.create()
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .withClaim("email", body.email)
                    .withClaim("role", role)
                    .withExpiresAt(Date(System.currentTimeMillis() + 36_000_00)) // 1 hodina
                    .sign(Algorithm.HMAC256(jwtSecret))

                call.respond(LoginResponse(token, role))
            }
        }
    }
}
