package jezdibolt.api

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import jezdibolt.model.UserDTO
import jezdibolt.service.UserService

fun Application.userApi(userService: UserService = UserService()) {
    routing {
        route("/users") {
            get {
                call.respond(userService.getAllUsers())
            }

            post {
                val user = call.receive<UserDTO>()
                val created = userService.createUser(user)
                call.respond(created)
            }
        }
    }
}
