package jezdibolt.api

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import java.util.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.response.*

/**
 * Uchovává seznam všech aktivních WebSocket připojení
 */
object WebSocketConnections {
    private val connections = Collections.synchronizedSet<DefaultWebSocketServerSession?>(LinkedHashSet())

    fun add(session: DefaultWebSocketServerSession) {
        connections.add(session)
    }

    fun remove(session: DefaultWebSocketServerSession) {
        connections.remove(session)
    }

    suspend fun broadcast(message: String) {
        val toRemove = mutableListOf<DefaultWebSocketServerSession>()
        for (conn in connections) {
            try {
                conn?.send(Frame.Text(message))
            } catch (e: Exception) {
                toRemove.add(conn)
            }
        }
        toRemove.forEach { remove(it) }
    }
}

/**
 * WebSocket endpoint `/updates`
 * Připojení musí být autentizováno přes JWT (auth-jwt)
 */

fun Application.webSocketApi() {
    routing {
        authenticate("auth-jwt") {
            webSocket("/updates") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", Int::class)
                val role = principal?.getClaim("role", String::class)

                WebSocketConnections.add(this)
                println("✅ User #$userId ($role) connected to /updates")

                try {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            val received = frame.readText()
                            println("📩 Message from $userId: $received")
                        }
                    }
                } finally {
                    WebSocketConnections.remove(this)
                    println("❌ User #$userId disconnected")
                }
            }
        }

        //  Pomocný endpoint pro testování WebSocket broadcastu
        get("/test-broadcast") {
            val message = "🟢 Test update from server at ${System.currentTimeMillis()}"
            WebSocketConnections.broadcast(message)
            call.respondText("Sent to all WebSocket clients: $message")
        }
    }
}
