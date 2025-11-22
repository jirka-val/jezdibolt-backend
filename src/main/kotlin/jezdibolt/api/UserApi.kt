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

                // 🔍 DETAIL UŽIVATELE + PRÁVA (pro editaci v modálu práv)
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

                // ✏️ ULOŽENÍ PRÁV (Admin nastavuje jinému userovi detailní oprávnění)
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

                // ✏️ EDITACE UŽIVATELE (Změna jména, role, hesla...)
                put("/{id}") {
                    val currentUser = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)

                    // Kontrola práv
                    if (!userService.hasPermission(currentUser.id, "EDIT_USERS") && currentUser.role != "owner") {
                        return@put call.respond(HttpStatusCode.Forbidden)
                    }

                    // Přijímáme UpdateUserRequest (heslo je volitelné)
                    val req = call.receive<UpdateUserRequest>()
                    val success = userService.updateUser(id, req)

                    if (success) {
                        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                    }
                }

                // ➕ VYTVOŘENÍ UŽIVATELE (Admin zakládá nového)
                post {
                    // 1. LOGOVÁNÍ UŽIVATELE
                    val currentUser = call.authUser()
                    call.application.log.info("🚀 POST /users request od: ${currentUser?.email} (Role: ${currentUser?.role}, ID: ${currentUser?.id})")

                    if (currentUser == null) {
                        call.application.log.warn("❌ POST /users - Unauthorized (No User)")
                        return@post call.respond(HttpStatusCode.Unauthorized)
                    }

                    // 2. LOGOVÁNÍ OPRÁVNĚNÍ
                    val hasPerm = userService.hasPermission(currentUser.id, "EDIT_USERS")
                    val isOwner = currentUser.role == "owner"
                    call.application.log.info("🔐 Oprávnění check: EDIT_USERS=$hasPerm, isOwner=$isOwner")

                    if (!hasPerm && !isOwner) {
                        call.application.log.warn("⛔ POST /users - Forbidden pro uživatele ${currentUser.email}")
                        return@post call.respond(HttpStatusCode.Forbidden)
                    }

                    try {
                        // 3. LOGOVÁNÍ PAYLOADU (Zkusíme přijmout data)
                        call.application.log.info("📥 Pokus o načtení CreateUserRequest...")
                        val userReq = call.receive<CreateUserRequest>()
                        call.application.log.info("✅ Přijata data: Email=${userReq.email}, Jméno=${userReq.name}, Role=${userReq.role}, CompanyId=${userReq.companyId}")

                        // 4. LOGOVÁNÍ AKCE (Vytvoření)
                        val created = userService.createUser(userReq)
                        call.application.log.info("🎉 Uživatel vytvořen s ID: ${created.id}")

                        call.respond(HttpStatusCode.Created, created)

                    } catch (e: ContentTransformationException) {
                        // Specifická chyba deserializace (špatný JSON)
                        call.application.log.error("❌ Chyba při čtení JSONu: ${e.message}", e)
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid JSON format: ${e.message}"))
                    } catch (e: Exception) {
                        // Ostatní chyby (DB, logika)
                        call.application.log.error("❌ Obecná chyba při vytváření uživatele: ${e.message}", e)
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