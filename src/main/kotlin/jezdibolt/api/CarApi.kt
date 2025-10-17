package jezdibolt.api

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.Car
import jezdibolt.service.CarService
import jezdibolt.service.HistoryService
import jezdibolt.util.authUser
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.time.LocalDate

@Serializable
data class CarDto(
    val id: Int,
    val licensePlate: String,
    val brand: String,
    val model: String,
    val year: Int,
    val fuelType: String,
    val stkValidUntil: String?,
    val color: String?,
    val city: String,
    val notes: String?,
    val photoUrl: String?
)

fun Car.toDto() = CarDto(
    id = id.value,
    licensePlate = licensePlate,
    brand = brand,
    model = model,
    year = year,
    fuelType = fuelType,
    stkValidUntil = stkValidUntil?.toString(),
    color = color,
    city = city,
    notes = notes,
    photoUrl = photoUrl
)

@Serializable
data class CarRequest(
    val licensePlate: String,
    val brand: String,
    val model: String,
    val year: Int,
    val fuelType: String,
    val stkValidUntil: String?,
    val color: String?,
    val city: String,
    val notes: String? = null
)

fun Application.carApi(carService: CarService = CarService()) {
    routing {
        route("/cars") {

            // 🔒 Vše pod JWT ochranou
            authenticate("auth-jwt") {

                // 🧾 Všechna auta
                get {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val cars = carService.listCars().map { it.toDto() }

                    call.application.log.info("🚗 ${user.email} (${user.role}) requested car list")
                    call.respond(HttpStatusCode.OK, cars)
                }

                // 🧾 Detail auta
                get("/{id}") {
                    val user = call.authUser() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                    val car = carService.getCar(id)
                    if (car == null) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Car not found"))
                    } else {
                        call.application.log.info("🔍 ${user.email} viewed car $id (${car.licensePlate})")
                        call.respond(HttpStatusCode.OK, car.toDto())
                    }
                }

                // 🆕 Vytvoření auta
                post {
                    val user = call.authUser() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    try {
                        val req = call.receive<CarRequest>()
                        val car = carService.createCar {
                            licensePlate = req.licensePlate
                            brand = req.brand
                            model = req.model
                            year = req.year
                            fuelType = req.fuelType
                            stkValidUntil = req.stkValidUntil?.let { LocalDate.parse(it) }
                            color = req.color
                            city = req.city
                            notes = req.notes
                        }

                        // 🧾 Log
                        HistoryService.log(
                            adminId = user.id,
                            action = "CREATE_CAR",
                            entity = "Car",
                            entityId = car.id.value,
                            details = "Uživatel ${user.email} (${user.role}) vytvořil auto ${car.licensePlate}"
                        )

                        WebSocketConnections.broadcast(
                            """{"type":"car_created","id":${car.id.value},"licensePlate":"${car.licensePlate}"}"""
                        )

                        call.application.log.info("✅ ${user.email} vytvořil auto ${car.licensePlate}")
                        call.respond(HttpStatusCode.Created, car.toDto())

                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Invalid request", "details" to (e.message ?: "unknown"))
                        )
                    }
                }

                // 📸 Nahrání fotky
                post("/{id}/photo") {
                    val user = call.authUser() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    try {
                        val id = call.parameters["id"]?.toIntOrNull()
                            ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                        val multipart = call.receiveMultipart()
                        var fileName: String? = null

                        multipart.forEachPart { part ->
                            if (part is PartData.FileItem) {
                                val ext = File(part.originalFileName ?: "").extension.ifBlank { "jpg" }
                                fileName = "car_${id}.$ext"

                                val uploadDir = File("uploads")
                                if (!uploadDir.exists()) uploadDir.mkdirs()

                                val file = File(uploadDir, fileName!!)
                                part.streamProvider().use { input ->
                                    file.outputStream().buffered().use { output -> input.copyTo(output) }
                                }
                            }
                            part.dispose()
                        }

                        if (fileName == null) {
                            return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
                        }

                        transaction {
                            val car = Car.findById(id) ?: error("Car not found")
                            car.photoUrl = "/uploads/$fileName"
                        }

                        HistoryService.log(
                            adminId = user.id,
                            action = "UPDATE_CAR_PHOTO",
                            entity = "Car",
                            entityId = id,
                            details = "Uživatel ${user.email} (${user.role}) nahrál fotku pro auto ID=$id"
                        )

                        WebSocketConnections.broadcast(
                            """{"type":"car_photo_updated","id":$id,"url":"/uploads/$fileName"}"""
                        )

                        call.respond(HttpStatusCode.OK, mapOf("status" to "photo uploaded", "url" to "/uploads/$fileName"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
                    }
                }

                // ✏️ Aktualizace auta
                put("/{id}") {
                    val user = call.authUser() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                    val req = call.receive<CarRequest>()
                    val updated = carService.updateCar(id) {
                        licensePlate = req.licensePlate
                        brand = req.brand
                        model = req.model
                        year = req.year
                        fuelType = req.fuelType
                        stkValidUntil = req.stkValidUntil?.let { LocalDate.parse(it) }
                        color = req.color
                        city = req.city
                        notes = req.notes
                    }

                    if (updated == null) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Car not found"))
                    } else {
                        HistoryService.log(
                            adminId = user.id,
                            action = "UPDATE_CAR",
                            entity = "Car",
                            entityId = id,
                            details = "Uživatel ${user.email} (${user.role}) upravil auto ${req.licensePlate}"
                        )

                        WebSocketConnections.broadcast(
                            """{"type":"car_updated","id":$id,"licensePlate":"${req.licensePlate}"}"""
                        )

                        call.respond(HttpStatusCode.OK, updated.toDto())
                    }
                }

                // Smazání auta
                delete("/{id}") {
                    val user = call.authUser() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                    val deleted = carService.deleteCar(id)
                    if (deleted) {
                        HistoryService.log(
                            adminId = user.id,
                            action = "DELETE_CAR",
                            entity = "Car",
                            entityId = id,
                            details = "Uživatel ${user.email} (${user.role}) smazal auto ID=$id"
                        )

                        WebSocketConnections.broadcast("""{"type":"car_deleted","id":$id}""")
                        call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Car not found"))
                    }
                }
            }
        }
    }
}
