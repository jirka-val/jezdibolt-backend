package jezdibolt.api

import jezdibolt.model.UserDTO
import jezdibolt.service.UserService
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoutes(userService: UserService = UserService()) {

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
