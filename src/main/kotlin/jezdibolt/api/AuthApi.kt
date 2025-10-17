package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.config.JwtConfig
import jezdibolt.model.UsersSchema
import jezdibolt.service.PasswordHelper
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

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
                val token: String,
                val companyId: Int?
            )

            post("/login") {
                val body = runCatching { call.receive<LoginRequest>() }.getOrNull()
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid request body")
                    )

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
                val companyId = user[UsersSchema.companyId]?.value

                // ✅ Vygeneruj token pomocí centrální JwtConfig
                val token = JwtConfig.generateToken(
                    userId = userId,
                    email = email,
                    role = role,
                    companyId = companyId
                )

                // ✅ Volitelně: realtime event (pokud máš WebSocketConnections)
                try {
                    WebSocketConnections.broadcast(
                        """{"type":"user_logged_in","userId":$userId,"role":"$role","name":"$name"}"""
                    )
                } catch (e: Exception) {
                    call.application.log.warn("WebSocket broadcast failed: ${e.message}")
                }

                // ✅ Volitelně: logování přihlášení
                // HistoryLogger.logAction(userId, "login", "User", userId, "Uživatel se přihlásil")

                call.respond(
                    LoginResponse(
                        id = userId,
                        name = name,
                        role = role,
                        token = token,
                        companyId = companyId
                    )
                )

                call.application.log.info("👤 User $email ($role) logged in at ${LocalDateTime.now()}")
            }
        }
    }
}
