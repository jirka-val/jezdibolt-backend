package jezdibolt.service

import jezdibolt.model.*
import kotlinx.serialization.Serializable
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale
import java.nio.charset.Charset
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

class BoltImportService {

    @Serializable
    data class ImportResult(val imported: Int, val skipped: Int, val batchId: Int, val filename: String)

    @Serializable
    data class MultiImportResult(val results: List<ImportResult>) {
        val totalImported: Int = results.sumOf { it.imported }
        val totalSkipped: Int = results.sumOf { it.skipped }
    }

    fun importFiles(files: List<Pair<ByteArray, String>>): MultiImportResult {
        val results = files.map { (bytes, filename) ->
            importFile(bytes, filename)
        }
        return MultiImportResult(results)
    }

    private fun importFile(bytes: ByteArray, filename: String): ImportResult {
        // 🚫 kontrola duplicitního importu podle filename
        val existingBatch = transaction {
            ImportBatches.selectAll()
                .where { ImportBatches.filename eq filename }
                .singleOrNull()
        }
        if (existingBatch != null) {
            return ImportResult(imported = 0, skipped = 0, batchId = existingBatch[ImportBatches.id].value, filename = filename)
        }

        return when {
            filename.endsWith(".xlsx", ignoreCase = true) -> importXlsx(bytes, filename)
            filename.endsWith(".csv", ignoreCase = true)  -> importCsv(bytes, filename)
            else -> error("Nepodporovaný formát souboru: $filename")
        }
    }

    // ---------------- XLSX ----------------
    private fun importXlsx(bytes: ByteArray, filename: String): ImportResult {
        val isoWeek = parseWeekFromFilename(filename)
        val (company, city) = parseCompanyAndCity(filename)

        val wb = XSSFWorkbook(ByteArrayInputStream(bytes))
        val sheet = wb.getSheetAt(0) ?: error("Sheet1 nenalezen")

        // 🏢 Získání nebo vytvoření firmy (pro vazbu na uživatele)
        // ✅ OPRAVA: Explicitní transakce a získání ID firmy
        val companyId = transaction {
            findOrCreateCompany(company, city)
        }

        val batchId = transaction {
            ImportBatches.insertAndGetId {
                it[ImportBatches.filename] = filename
                it[ImportBatches.isoWeek] = isoWeek
                it[ImportBatches.company] = company
                it[ImportBatches.city] = city
            }
        }

        val headerRow = sheet.getRow(0) ?: error("Prázdný soubor – chybí hlavička")
        val headerIndex = (0 until headerRow.physicalNumberOfCells).associateBy { idx ->
            normalizeHeader(headerRow.getCell(idx)?.toString() ?: "")
        }

        fun idx(name: String) = headerIndex[name]
            ?: error("Ve hlavičce chybí sloupec: $name")

        val COL_NAME      = idx("ridic")
        val COL_EMAIL     = idx("e-mail")
        val COL_DRIVER_ID = idx("identifikator ridice")
        val COL_UNIQ_ID   = idx("jedinecny identifikator")
        val COL_CONTACT   = headerIndex["telefonni cislo"]

        val COL_GROSS_TOTAL = headerIndex["hruby vydelek (celkem)|kc"]
        val COL_H_GROSS     = headerIndex["hruby vydelek za hodinu|kc/hod."]
        val COL_CASH_TAKEN  = headerIndex["vybrana hotovost|kc"]
        val COL_TIPS        = headerIndex["spropitne|kc"]

        var imported = 0
        var skipped = 0

        transaction {
            for (r in 1..sheet.lastRowNum) {
                val row = sheet.getRow(r) ?: continue

                val email = row.getCell(COL_EMAIL)?.stringValue()?.lowercase(Locale.getDefault())
                    ?: continue
                if (email.isBlank()) continue

                val name = row.getCell(COL_NAME)?.stringValue()?.ifBlank { null }
                val driverId = row.getCell(COL_DRIVER_ID)?.stringValue()
                val uniqId   = row.getCell(COL_UNIQ_ID)?.stringValue()
                val contact  = COL_CONTACT?.let { row.getCell(it)?.stringValue()?.ifBlank { "" } }

                // 🔗 Předáváme companyId pro přiřazení uživatele k firmě
                val userId = findOrCreateUserByEmail(email, name, contact, companyId)

                if (!uniqId.isNullOrBlank()) {
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

                val grossTotal = dec(COL_GROSS_TOTAL)
                val hourlyGross = dec(COL_H_GROSS)
                val cashTaken = dec(COL_CASH_TAKEN) ?: BigDecimal.ZERO
                val tips = dec(COL_TIPS) ?: BigDecimal.ZERO

                val hoursWorked: BigDecimal = if (hourlyGross != null && hourlyGross > BigDecimal.ZERO && grossTotal != null) {
                    grossTotal.divide(hourlyGross, 2, java.math.RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                val appliedRate = PayoutService.getAppliedRate(hoursWorked.toDouble(), hourlyGross?.toDouble() ?: 0.0)
                val earnings = (BigDecimal(appliedRate) * hoursWorked) + tips
                val settlement = earnings - cashTaken

                BoltEarnings.insert {
                    it[BoltEarnings.userId] = userId
                    it[BoltEarnings.batchId] = batchId
                    it[BoltEarnings.driverIdentifier] = driverId
                    it[BoltEarnings.uniqueIdentifier] = uniqId

                    it[BoltEarnings.grossTotal]  = grossTotal
                    it[BoltEarnings.hourlyGross] = hourlyGross
                    it[BoltEarnings.cashTaken]   = cashTaken
                    it[BoltEarnings.tips]        = tips

                    it[BoltEarnings.hoursWorked] = hoursWorked
                    it[BoltEarnings.appliedRate] = appliedRate
                    it[BoltEarnings.earnings]    = earnings
                    it[BoltEarnings.settlement]  = settlement
                }

                imported++
            }
        }

        wb.close()
        return ImportResult(imported, skipped, batchId.value, filename)
    }

    // ---------------- CSV ----------------
    private fun importCsv(bytes: ByteArray, filename: String): ImportResult {
        val isoWeek = parseWeekFromFilename(filename)
        val (company, city) = parseCompanyAndCity(filename)

        val charset = when {
            bytes.size >= 3 &&
                    bytes[0] == 0xEF.toByte() &&
                    bytes[1] == 0xBB.toByte() &&
                    bytes[2] == 0xBF.toByte() -> Charsets.UTF_8
            else -> Charset.forName("windows-1250")
        }

        val firstLine = bytes.inputStream().bufferedReader(charset).readLine()
        val delimiter = if (firstLine.contains(";")) ';' else ','

        val reader = bytes.inputStream().bufferedReader(charset)
        val parser = CSVParser(reader, CSVFormat.DEFAULT
            .withDelimiter(delimiter)
            .withFirstRecordAsHeader()
            .withIgnoreSurroundingSpaces()
        )

        val headerIndex = parser.headerMap.keys.associateBy { normalizeHeader(it) }

        fun idx(name: String) = headerIndex[name]
            ?: error("Ve hlavičce chybí sloupec: $name. Načtené: ${headerIndex.keys}")

        val COL_NAME      = idx("ridic")
        val COL_EMAIL     = idx("e-mail")
        val COL_DRIVER_ID = idx("identifikator ridice")
        val COL_UNIQ_ID   = idx("jedinecny identifikator")
        val COL_CONTACT   = headerIndex["telefonni cislo"]

        val COL_GROSS_TOTAL = headerIndex["hruby vydelek (celkem)|kc"]
        val COL_H_GROSS     = headerIndex["hruby vydelek za hodinu|kc/hod."]
        val COL_CASH_TAKEN  = headerIndex["vybrana hotovost|kc"]
        val COL_TIPS        = headerIndex["spropitne|kc"]

        var imported = 0
        var skipped = 0

        // 🏢 Získání nebo vytvoření firmy
        val companyId = transaction {
            findOrCreateCompany(company, city)
        }

        val batchId = transaction {
            ImportBatches.insertAndGetId {
                it[ImportBatches.filename] = filename
                it[ImportBatches.isoWeek] = isoWeek
                it[ImportBatches.company] = company
                it[ImportBatches.city] = city
            }
        }

        transaction {
            for (record in parser) {
                val email = record.get(COL_EMAIL).trim().lowercase(Locale.getDefault())
                if (email.isBlank()) continue

                val name = record.get(COL_NAME).ifBlank { null }
                val driverId = record.get(COL_DRIVER_ID)
                val uniqId   = record.get(COL_UNIQ_ID)
                val contact  = COL_CONTACT?.let { record.get(it) }

                // 🔗 Předáváme companyId
                val userId = findOrCreateUserByEmail(email, name, contact, companyId)

                if (!uniqId.isNullOrBlank()) {
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

                fun dec(col: String?): BigDecimal? =
                    col?.let { record.get(col).replace(",", ".").toBigDecimalOrNull() }

                val grossTotal = dec(COL_GROSS_TOTAL)
                val hourlyGross = dec(COL_H_GROSS)
                val cashTaken = dec(COL_CASH_TAKEN) ?: BigDecimal.ZERO
                val tips = dec(COL_TIPS) ?: BigDecimal.ZERO

                val hoursWorked: BigDecimal = if (hourlyGross != null && hourlyGross > BigDecimal.ZERO && grossTotal != null) {
                    grossTotal.divide(hourlyGross, 2, java.math.RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                val appliedRate = PayoutService.getAppliedRate(hoursWorked.toDouble(), hourlyGross?.toDouble() ?: 0.0)
                val earnings = (BigDecimal(appliedRate) * hoursWorked) + tips
                val settlement = earnings - cashTaken

                BoltEarnings.insert {
                    it[BoltEarnings.userId] = userId
                    it[BoltEarnings.batchId] = batchId
                    it[BoltEarnings.driverIdentifier] = driverId
                    it[BoltEarnings.uniqueIdentifier] = uniqId

                    it[BoltEarnings.grossTotal]  = grossTotal
                    it[BoltEarnings.hourlyGross] = hourlyGross
                    it[BoltEarnings.cashTaken]   = cashTaken
                    it[BoltEarnings.tips]        = tips

                    it[BoltEarnings.hoursWorked] = hoursWorked
                    it[BoltEarnings.appliedRate] = appliedRate
                    it[BoltEarnings.earnings]    = earnings
                    it[BoltEarnings.settlement]  = settlement
                }

                imported++
            }
        }

        parser.close()
        return ImportResult(imported, skipped, batchId.value, filename)
    }

    // ---------------- Helpers ----------------
    private fun normalizeHeader(name: String): String {
        val base = name
            .replace("\uFEFF", "") // BOM
            .trim('"')
            .lowercase(Locale.getDefault())
        return Normalizer.normalize(base, Normalizer.Form.NFD)
            .replace("\\p{M}".toRegex(), "")
            .trim()
    }

    private fun parseCompanyAndCity(filename: String): Pair<String, String?> {
        val name = filename.substringBeforeLast(".")
        val parts = name.split("-")
        if (parts.size < 4) return "unknown" to null

        val afterDates = parts.drop(3).joinToString("-")
        val tokens = afterDates.split(" ")

        val city = tokens.firstOrNull()
        val fleetIndex = tokens.indexOfFirst { it.equals("Fleet", ignoreCase = true) }

        val company = if (fleetIndex != -1 && fleetIndex + 1 < tokens.size) {
            tokens.drop(fleetIndex + 1).joinToString(" ")
        } else afterDates

        return company.trim() to city
    }

    /**
     * Najde nebo vytvoří firmu v tabulce Companies.
     * 🛠 OPRAVA: Použito selectAll().andWhere() místo select(), aby se předešlo
     * konfliktům při kompilaci a chybám s načítáním sloupců.
     */
    private fun findOrCreateCompany(companyName: String, cityName: String?): EntityID<Int> {
        val existingId = Companies
            .selectAll()
            .andWhere { Companies.name eq companyName }
            .map { it[Companies.id] } // Bezpečně vytáhneme ID z celého řádku
            .singleOrNull()

        return if (existingId != null) {
            existingId
        } else {
            Companies.insertAndGetId {
                it[name] = companyName
                it[city] = cityName
            }
        }
    }

    /**
     * Najde nebo vytvoří uživatele a přiřadí ho k firmě.
     */
    private fun findOrCreateUserByEmail(
        email: String,
        nameOrNull: String?,
        contactOrNull: String?,
        companyId: EntityID<Int> // 🆕 Přidáno companyId
    ): EntityID<Int> {
        val existing = UsersSchema.selectAll().where { UsersSchema.email eq email }.singleOrNull()
        if (existing != null) {
            // Pokud uživatel existuje, vracíme jeho ID.
            return existing[UsersSchema.id]
        }

        val defaultPassword = "Default123"
        val hashed = org.mindrot.jbcrypt.BCrypt.hashpw(defaultPassword, org.mindrot.jbcrypt.BCrypt.gensalt())

        return UsersSchema.insertAndGetId {
            it[UsersSchema.name] = nameOrNull ?: email.substringBefore("@")
            it[UsersSchema.email] = email
            it[UsersSchema.contact] = contactOrNull ?: ""
            it[UsersSchema.role] = "driver"
            it[UsersSchema.passwordHash] = hashed
            it[UsersSchema.companyId] = companyId // 🆕 Uložíme vazbu na firmu
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
        val regex = Regex("(\\d{2}_\\d{2}_\\d{4})-(\\d{2}_\\d{2}_\\d{4})")
        val match = regex.find(filename) ?: return "unknown"

        val startDateStr = match.groupValues[1]
        val formatter = DateTimeFormatter.ofPattern("dd_MM_uuuu")
        val startDate = LocalDate.parse(startDateStr, formatter)

        val weekFields = WeekFields.ISO
        val weekNumber = startDate.get(weekFields.weekOfWeekBasedYear())
        val year = startDate.get(weekFields.weekBasedYear())

        return String.format("%d-W%02d", year, weekNumber)
    }
}