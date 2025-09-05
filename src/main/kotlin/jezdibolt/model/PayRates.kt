package jezdibolt.model

import org.jetbrains.exposed.dao.id.IntIdTable

object PayRates : IntIdTable("pay_rates") {
    val minGross = integer("min_gross")   // od kolika hrubého / hod
    val maxGross = integer("max_gross").nullable() // do kolika hrubého / hod (null = nekonečno)
    val rate = integer("rate_per_hour")   // základní sazba za hodinu
}
