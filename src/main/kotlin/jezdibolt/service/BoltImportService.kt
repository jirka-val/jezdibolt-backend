package jezdibolt.service

import jezdibolt.model.*
import kotlinx.serialization.Serializable
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.util.Locale

import org.jetbrains.exposed.sql.and

class BoltImportService {

    @Serializable
    data class ImportResult(val imported: Int, val skipped: Int, val batchId: Int)

    fun importXlsx(bytes: ByteArray, filename: String): ImportResult {
        val isoWeek = parseWeekFromFilename(filename)

        val wb = XSSFWorkbook(ByteArrayInputStream(bytes))
        val sheet = wb.getSheetAt(0) ?: error("Sheet1 nenalezen")

        // batchId as EntityID<Int>
        val batchId = transaction {
            ImportBatches.insertAndGetId {
                it[ImportBatches.filename] = filename
                it[ImportBatches.isoWeek] = isoWeek
            }
        }

        val headerRow = sheet.getRow(0) ?: error("Prázdný soubor – chybí hlavička")
        val headerIndex = (0 until headerRow.physicalNumberOfCells).associateBy { idx ->
            headerRow.getCell(idx)?.toString()?.trim() ?: ""
        }

        fun idx(name: String) = headerIndex[name]
            ?: error("Ve hlavičce chybí sloupec: $name")

        val COL_NAME      = idx("Řidič")
        val COL_EMAIL     = idx("E-mail")
        val COL_DRIVER_ID = idx("Identifikátor řidiče")
        val COL_UNIQ_ID   = idx("Jedinečný identifikátor")
        val COL_CONTACT   = headerIndex["Telefonní číslo"]

        val COL_GROSS_TOTAL = headerIndex["Hrubý výdělek (celkem)|Kč"]
        val COL_GROSS_APP   = headerIndex["Hrubý výdělek (platby v aplikaci)|Kč"]
        val COL_GROSS_CASH  = headerIndex["Hrubý výdělek (platby v hotovosti)|Kč"]
        val COL_TIPS        = headerIndex["Spropitné|Kč"]
        val COL_NET         = headerIndex["Čisté výdělky|Kč"]
        val COL_H_GROSS     = headerIndex["Hrubý výdělek za hodinu|Kč/hod."]
        val COL_H_NET       = headerIndex["Čistý výdělek za hodinu|Kč/hod."]
        val COL_CASH_TAKEN  = headerIndex["Vybraná hotovost|Kč"]

        var imported = 0
        var skipped = 0

        transaction {
            for (r in 1..sheet.lastRowNum) {
                val row = sheet.getRow(r) ?: continue

                val email = row.getCell(COL_EMAIL)?.stringValue()?.lowercase(Locale.getDefault())
                    ?: continue

                val name = row.getCell(COL_NAME)?.stringValue()?.ifBlank { null }
                val driverId = row.getCell(COL_DRIVER_ID)?.stringValue()
                val uniqId   = row.getCell(COL_UNIQ_ID)?.stringValue()

                val contact = COL_CONTACT?.let { row.getCell(it)?.stringValue()?.ifBlank { "" } }
                val userId = findOrCreateUserByEmail(email, name, contact)


                if (uniqId != null) {
                    val exists = BoltEarnings
                        .selectAll()
                        .where { (BoltEarnings.uniqueIdentifier eq uniqId) and (BoltEarnings.batchId eq batchId) }
                        .limit(1)
                        .any()

                    if (exists) {
                        skipped++
                        continue
                    }
                }

                fun dec(col: Int?) = col?.let { row.getCell(it)?.decimalOrNull() }

                BoltEarnings.insert {
                    it[BoltEarnings.userId] = userId
                    it[BoltEarnings.batchId] = batchId
                    it[BoltEarnings.driverIdentifier] = driverId
                    it[BoltEarnings.uniqueIdentifier] = uniqId

                    it[BoltEarnings.grossTotal]  = dec(COL_GROSS_TOTAL)
                    it[BoltEarnings.grossApp]    = dec(COL_GROSS_APP)
                    it[BoltEarnings.grossCash]   = dec(COL_GROSS_CASH)
                    it[BoltEarnings.tips]        = dec(COL_TIPS)
                    it[BoltEarnings.net]         = dec(COL_NET)
                    it[BoltEarnings.hourlyGross] = dec(COL_H_GROSS)
                    it[BoltEarnings.hourlyNet]   = dec(COL_H_NET)
                    it[BoltEarnings.cashTaken]   = dec(COL_CASH_TAKEN)

                }

                imported++
            }
        }

        wb.close()
        return ImportResult(imported, skipped, batchId.value)
    }

    private fun findOrCreateUserByEmail(email: String, nameOrNull: String?, contactOrNull: String?): EntityID<Int> {
        val existing = UsersSchema.selectAll().where { UsersSchema.email eq email }.singleOrNull()
        if (existing != null) return existing[UsersSchema.id]

        // defaultni heslo zatim
        val defaultPassword = "Default123"
        val hashed = org.mindrot.jbcrypt.BCrypt.hashpw(defaultPassword, org.mindrot.jbcrypt.BCrypt.gensalt())

        return UsersSchema.insertAndGetId {
            it[UsersSchema.name] = nameOrNull ?: email.substringBefore("@")
            it[UsersSchema.email] = email
            it[UsersSchema.contact] = contactOrNull ?: ""
            it[UsersSchema.role] = "driver"
            it[UsersSchema.passwordHash] = hashed
        }
    }


    private fun org.apache.poi.ss.usermodel.Cell.stringValue(): String =
        when (cellType) {
            CellType.STRING  -> stringCellValue.trim()
            CellType.NUMERIC -> numericCellValue.toLong().toString()
            else -> toString().trim()
        }

    private fun org.apache.poi.ss.usermodel.Cell?.decimalOrNull(): BigDecimal? = when (this?.cellType) {
        CellType.NUMERIC -> BigDecimal.valueOf(this.numericCellValue)
        CellType.STRING  -> this.stringCellValue.replace(",", ".").toBigDecimalOrNull()
        else -> null
    }

    private fun parseWeekFromFilename(filename: String): String {
        val weekRaw = Regex("(\\d{4}W\\d{2})").find(filename)?.groupValues?.get(1)
        val week = weekRaw?.let { it.substring(0,4) + "-W" + it.substring(5) } ?: "unknown"
        return week
    }
}
