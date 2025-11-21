package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.UpdatePermissionsRequest
import jezdibolt.model.UserDTO
import jezdibolt.service.UserService
import jezdibolt.util.authUser

fun Application.userApi(userService: UserService = UserService()) {
    routing {
        route("/users") {
            // 🔒 Všechny endpointy pod zámkem
            authenticate("auth-jwt") {

                // 📋 SEZNAM UŽIVATELŮ (Filtrovaný!)
                get {
                    val currentUser = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    // 1. Kontrola, jestli má právo vidět seznam uživatelů
                    if (!userService.hasPermission(currentUser.id, "VIEW_USERS") && currentUser.role != "owner") {
                        return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Nemáš právo prohlížet uživatele"))
                    }

                    // 2. Vrátíme filtrovaný seznam
                    val users = userService.getAllUsers(currentUser.id, currentUser.role)
                    call.respond(users)
                }

                // 🔍 DETAIL UŽIVATELE + PRÁVA (pro editaci)
                get("/{id}/permissions") {
                    val currentUser = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)

                    // Musí mít právo editovat uživatele
                    if (!userService.hasPermission(currentUser.id, "EDIT_USERS") && currentUser.role != "owner") {
                        return@get call.respond(HttpStatusCode.Forbidden)
                    }

                    val detail = userService.getUserWithRights(id)
                    if (detail == null) call.respond(HttpStatusCode.NotFound) else call.respond(detail)
                }

                // ✏️ ULOŽENÍ PRÁV (Admin nastavuje jinému userovi)
                put("/{id}/permissions") {
                    val currentUser = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)

                    if (!userService.hasPermission(currentUser.id, "EDIT_USERS") && currentUser.role != "owner") {
                        return@put call.respond(HttpStatusCode.Forbidden)
                    }

                    val body = call.receive<UpdatePermissionsRequest>()
                    userService.updateUserPermissions(id, body)

                    call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                }

                // ➕ VYTVOŘENÍ UŽIVATELE
                post {
                    val currentUser = call.authUser() ?: return@post call.respond(HttpStatusCode.Unauthorized)

                    // Jen owner nebo admin s právem může zakládat
                    if (!userService.hasPermission(currentUser.id, "EDIT_USERS") && currentUser.role != "owner") {
                        return@post call.respond(HttpStatusCode.Forbidden)
                    }

                    val user = call.receive<UserDTO>()
                    val created = userService.createUser(user)
                    call.respond(HttpStatusCode.Created, created)
                }
            }
        }
    }
}