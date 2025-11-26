package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.CreateUserRequest
import jezdibolt.model.UpdatePermissionsRequest
import jezdibolt.model.UpdateUserRequest
import jezdibolt.service.HistoryService
import jezdibolt.service.UserService
import jezdibolt.util.authUser

fun Application.userApi(userService: UserService = UserService()) {
    routing {
        route("/users") {
            authenticate("auth-jwt") {

                get {
                    val currentUser = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    if (!userService.hasPermission(currentUser.id, "VIEW_USERS")) {
                        return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nemáte oprávnění prohlížet uživatele"))
                    }

                    val users = userService.getAllUsers(currentUser.id, currentUser.role)
                    call.respond(users)
                }

                get("/{id}/permissions") {
                    val currentUser = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)

                    // Musí mít právo editovat uživatele
                    if (!userService.hasPermission(currentUser.id, "EDIT_USERS")) {
                        return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nemáte oprávnění spravovat práva"))
                    }

                    val detail = userService.getUserWithRights(id)
                    if (detail == null) call.respond(HttpStatusCode.NotFound) else call.respond(detail)
                }

                put("/{id}/permissions") {
                    val currentUser = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)

                    if (!userService.hasPermission(currentUser.id, "EDIT_USERS")) {
                        return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nemáte oprávnění upravovat práva"))
                    }

                    val body = call.receive<UpdatePermissionsRequest>()
                    userService.updateUserPermissions(id, body)

                    HistoryService.log(
                        adminId = currentUser.id,
                        action = "UPDATE_PERMISSIONS",
                        entity = "User",
                        entityId = id,
                        details = "Uživatel ${currentUser.email} aktualizoval oprávnění pro uživatele ID=$id"
                    )

                    WebSocketConnections.broadcast("""{"type":"user_permissions_updated","userId":$id}""")

                    call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                }

                put("/{id}") {
                    val currentUser = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)

                    if (!userService.hasPermission(currentUser.id, "EDIT_USERS")) {
                        return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nemáte oprávnění upravovat uživatele"))
                    }

                    val req = call.receive<UpdateUserRequest>()
                    val success = userService.updateUser(id, req)

                    if (success) {
                        HistoryService.log(
                            adminId = currentUser.id,
                            action = "UPDATE_USER",
                            entity = "User",
                            entityId = id,
                            details = "Uživatel ${currentUser.email} upravil uživatele ID=$id"
                        )

                        WebSocketConnections.broadcast("""{"type":"user_updated","userId":$id}""")

                        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                    }
                }

                // ➕ VYTVOŘENÍ UŽIVATELE (Admin zakládá nového)
                post {
                    val currentUser = call.authUser() ?: return@post call.respond(HttpStatusCode.Unauthorized)

                    if (!userService.hasPermission(currentUser.id, "EDIT_USERS")) {
                        return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nemáte oprávnění vytvářet uživatele"))
                    }

                    try {
                        val userReq = call.receive<CreateUserRequest>()
                        val created = userService.createUser(userReq)

                        // 🧾 Log
                        HistoryService.log(
                            adminId = currentUser.id,
                            action = "CREATE_USER",
                            entity = "User",
                            entityId = created.id,
                            details = "Uživatel ${currentUser.email} vytvořil uživatele ${created.email} (${created.role})"
                        )

                        WebSocketConnections.broadcast("""{"type":"user_created","userId":${created.id}}""")

                        call.application.log.info("✅ Uživatel vytvořen: ${created.email}")
                        call.respond(HttpStatusCode.Created, created)

                    } catch (e: ContentTransformationException) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid JSON format: ${e.message}"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to (e.message ?: "Failed to create user"))
                        )
                    }
                }
            }
        }
    }
}