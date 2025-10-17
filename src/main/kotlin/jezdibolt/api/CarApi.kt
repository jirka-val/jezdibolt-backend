package jezdibolt.api

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jezdibolt.model.Car
import jezdibolt.service.CarService
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

            // 🔹 všechny auta
            get {
                val cars = carService.listCars().map { it.toDto() }
                call.respond(HttpStatusCode.OK, cars)
            }

            // 🔹 detail auta
            get("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                val car = carService.getCar(id)
                if (car == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Car not found"))
                } else {
                    call.respond(HttpStatusCode.OK, car.toDto())
                }
            }

            // 🔹 vytvoření auta
            post {
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

                    // ✅ realtime broadcast – nové auto
                    WebSocketConnections.broadcast(
                        """{"type":"car_created","id":${car.id.value},"licensePlate":"${car.licensePlate}"}"""
                    )

                    call.respond(HttpStatusCode.Created, car.toDto())

                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid request", "details" to (e.message ?: "unknown"))
                    )
                }
            }

            // 🔹 nahrání fotky
            post("/{id}/photo") {
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

                    // ✅ realtime broadcast – nová fotka
                    WebSocketConnections.broadcast(
                        """{"type":"car_photo_updated","id":$id,"url":"/uploads/$fileName"}"""
                    )

                    call.respond(HttpStatusCode.OK, mapOf("status" to "photo uploaded", "url" to "/uploads/$fileName"))
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
                }
            }

            // 🔹 aktualizace auta
            put("/{id}") {
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
                    // ✅ realtime broadcast – úprava auta
                    WebSocketConnections.broadcast(
                        """{"type":"car_updated","id":$id,"licensePlate":"${req.licensePlate}"}"""
                    )

                    call.respond(HttpStatusCode.OK, updated.toDto())
                }
            }

            // 🔹 smazání auta
            delete("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid ID"))

                val deleted = carService.deleteCar(id)
                if (deleted) {
                    // ✅ realtime broadcast – odstranění auta
                    WebSocketConnections.broadcast("""{"type":"car_deleted","id":$id}""")

                    call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Car not found"))
                }
            }
        }
    }
}
