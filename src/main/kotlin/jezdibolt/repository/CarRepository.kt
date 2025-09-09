package jezdibolt.repository

import jezdibolt.model.Car
import jezdibolt.model.Cars
import org.jetbrains.exposed.sql.transactions.transaction

class CarRepository {
    fun getAll(): List<Car> = transaction {
        Car.all().toList()
    }

    fun getById(id: Int): Car? = transaction {
        Car.findById(id)
    }

    fun create(car: Car.() -> Unit): Car = transaction {
        Car.new { car() }
    }

    fun update(id: Int, block: Car.() -> Unit): Car? = transaction {
        val car = Car.findById(id)
        car?.apply(block)
    }

    fun delete(id: Int): Boolean = transaction {
        val car = Car.findById(id)
        car?.delete()
        car != null
    }
}
