package jezdibolt.model

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.date

object Cars : IntIdTable("cars") {
    val licensePlate = varchar("license_plate", 20).uniqueIndex()
    val brand = varchar("brand", 50)
    val model = varchar("model", 50)
    val year = integer("year")
    val fuelType = varchar("fuel_type", 20)
    val isTaxi = bool("is_taxi").default(false)
    val stkValidUntil = date("stk_valid_until").nullable()
    val color = varchar("color", 30).nullable()
    val status = varchar("status", 20).default("active")
    val notes = text("notes").nullable()
    val photoUrl = varchar("photo_url", 255).nullable()
}

