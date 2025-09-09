package jezdibolt.service

import jezdibolt.model.Car
import jezdibolt.repository.CarRepository

class CarService(
    private val carRepository: CarRepository = CarRepository()
) {
    fun listCars(): List<Car> = carRepository.getAll()
    fun getCar(id: Int): Car? = carRepository.getById(id)
    fun createCar(builder: Car.() -> Unit): Car = carRepository.create(builder)
    fun updateCar(id: Int, builder: Car.() -> Unit): Car? = carRepository.update(id, builder)
    fun deleteCar(id: Int): Boolean = carRepository.delete(id)
}
