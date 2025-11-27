package jezdibolt.service

import jezdibolt.model.BoltEarnings
import jezdibolt.model.ImportBatches
import jezdibolt.model.UsersSchema
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal

@Serializable
data class DriverPerformanceDto(
    val name: String,
    val gross: Double,
    val hours: Double,
    val earnings: Double
)

// 🟢 ZMĚNA 1: Upravíme DTO, aby obsahovalo seznam všech řidičů (allDrivers)
@Serializable
data class DashboardStatsDto(
    val currentWeek: String,
    val totalGross: Double,
    val totalNetEarnings: Double,
    val totalHours: Double,
    val activeDrivers: Long,
    val avgGrossPerHour: Double,
    val allDrivers: List<DriverPerformanceDto> // Zde posíláme všechny
)

object DashboardService {

    fun getStats(): DashboardStatsDto? {
        return transaction {
            // 1. Najdeme poslední import
            val lastBatch = ImportBatches.selectAll()
                .orderBy(ImportBatches.createdAt to SortOrder.DESC)
                .limit(1)
                .singleOrNull() ?: return@transaction null

            val batchId = lastBatch[ImportBatches.id].value
            val weekLabel = lastBatch[ImportBatches.isoWeek]

            // 2. Agregace dat
            val earningsInBatch = (BoltEarnings innerJoin UsersSchema)
                .selectAll()
                .where { BoltEarnings.batchId eq batchId }

            var sumGross = BigDecimal.ZERO
            var sumNet = BigDecimal.ZERO
            var sumHours = BigDecimal.ZERO
            var driverCount = 0L

            val driversList = mutableListOf<DriverPerformanceDto>()

            earningsInBatch.forEach { row ->
                val gross = row[BoltEarnings.grossTotal] ?: BigDecimal.ZERO
                val net = row[BoltEarnings.earnings] ?: BigDecimal.ZERO
                val hours = row[BoltEarnings.hoursWorked]

                sumGross = sumGross.add(gross)
                sumNet = sumNet.add(net)
                sumHours = sumHours.add(hours)
                driverCount++

                driversList.add(
                    DriverPerformanceDto(
                        name = row[UsersSchema.name],
                        gross = gross.toDouble(),
                        hours = hours.toDouble(),
                        earnings = net.toDouble()
                    )
                )
            }

            val avgRate = if (sumHours > BigDecimal.ZERO) {
                sumGross.divide(sumHours, 2, java.math.RoundingMode.HALF_UP).toDouble()
            } else 0.0

            // 🟢 ZMĚNA 2: Seřadíme všechny podle výdělku a pošleme je KOMPLETNĚ (žádné .take(3))
            val allSorted = driversList.sortedByDescending { it.gross }

            DashboardStatsDto(
                currentWeek = weekLabel,
                totalGross = sumGross.toDouble(),
                totalNetEarnings = sumNet.toDouble(),
                totalHours = sumHours.toDouble(),
                activeDrivers = driverCount,
                avgGrossPerHour = avgRate,
                allDrivers = allSorted // Předáváme celý seznam
            )
        }
    }
}