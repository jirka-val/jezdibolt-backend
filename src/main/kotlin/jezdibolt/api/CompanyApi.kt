package jezdibolt.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.Companies
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class CompanyDto(
    val id: Int,
    val name: String,
    val city: String?
)

fun Application.companyApi() {
    routing {
        route("/companies") {
            authenticate("auth-jwt") {
                get {
                    val companies = transaction {
                        Companies.selectAll().map {
                            CompanyDto(
                                id = it[Companies.id].value,
                                name = it[Companies.name],
                                city = it[Companies.city]
                            )
                        }
                    }
                    call.respond(HttpStatusCode.OK, companies)
                }
            }
        }
    }
}