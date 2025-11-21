package jezdibolt.repository

import jezdibolt.model.UserCityAccess
import jezdibolt.model.UserCompanyAccess
import jezdibolt.model.UserPermissions
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class UserRightsRepository {

    fun hasPermission(userId: Int, code: String): Boolean = transaction {
        UserPermissions.select(
            (UserPermissions.userId eq userId) and (UserPermissions.permissionCode eq code)
        ).any()
    }

    fun getUserPermissions(userId: Int): List<String> = transaction {
        UserPermissions.select(UserPermissions.userId eq userId)
            .map { it[UserPermissions.permissionCode] }
    }

    fun getAllowedCities(userId: Int): List<String> = transaction {
        UserCityAccess.select(UserCityAccess.userId eq userId)
            .map { it[UserCityAccess.city] }
    }

    fun getAllowedCompanies(userId: Int): List<Int> = transaction {
        UserCompanyAccess.select(UserCompanyAccess.userId eq userId)
            .map { it[UserCompanyAccess.companyId].value }
    }

    fun updatePermissions(userId: Int, codes: List<String>) = transaction {
        UserPermissions.deleteWhere { UserPermissions.userId eq userId }

        if (codes.isNotEmpty()) {
            UserPermissions.batchInsert(codes) { code ->
                this[UserPermissions.userId] = userId
                this[UserPermissions.permissionCode] = code
            }
        }
    }

    fun updateAccess(userId: Int, companyIds: List<Int>, cities: List<String>) = transaction {
        UserCompanyAccess.deleteWhere { UserCompanyAccess.userId eq userId }

        if (companyIds.isNotEmpty()) {
            UserCompanyAccess.batchInsert(companyIds) { cid ->
                this[UserCompanyAccess.userId] = userId
                this[UserCompanyAccess.companyId] = cid
            }
        }

        UserCityAccess.deleteWhere { UserCityAccess.userId eq userId }

        if (cities.isNotEmpty()) {
            UserCityAccess.batchInsert(cities) { city ->
                this[UserCityAccess.userId] = userId
                this[UserCityAccess.city] = city
            }
        }
    }
}