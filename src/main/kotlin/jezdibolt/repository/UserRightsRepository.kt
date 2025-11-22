package jezdibolt.repository

import jezdibolt.model.Companies
import jezdibolt.model.UserCityAccess
import jezdibolt.model.UserCompanyAccess
import jezdibolt.model.UserPermissions
import jezdibolt.model.UsersSchema
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class UserRightsRepository {

    fun hasPermission(userId: Int, code: String): Boolean = transaction {
        UserPermissions.selectAll().where {
            (UserPermissions.userId eq EntityID(userId, UsersSchema)) and (UserPermissions.permissionCode eq code)
        }.any()
    }

    fun getUserPermissions(userId: Int): List<String> = transaction {
        UserPermissions.selectAll().where {
            UserPermissions.userId eq EntityID(userId, UsersSchema)
        }.map { it[UserPermissions.permissionCode] }
    }

    fun getAllowedCities(userId: Int): List<String> = transaction {
        UserCityAccess.selectAll().where {
            UserCityAccess.userId eq EntityID(userId, UsersSchema)
        }.map { it[UserCityAccess.city] }
    }

    fun getAllowedCompanies(userId: Int): List<Int> = transaction {
        UserCompanyAccess.selectAll().where {
            UserCompanyAccess.userId eq EntityID(userId, UsersSchema)
        }.map { it[UserCompanyAccess.companyId].value }
    }

    fun updatePermissions(targetUserId: Int, codes: List<String>) = transaction {
        // ✅ OPRAVA: Místo "it.userId" voláme "UserPermissions.userId"
        UserPermissions.deleteWhere {
            UserPermissions.userId eq EntityID(targetUserId, UsersSchema)
        }

        if (codes.isNotEmpty()) {
            UserPermissions.batchInsert(codes) { code ->
                this[UserPermissions.userId] = EntityID(targetUserId, UsersSchema)
                this[UserPermissions.permissionCode] = code
            }
        }
    }

    fun updateAccess(targetUserId: Int, companyIds: List<Int>, cities: List<String>) = transaction {
        // ✅ OPRAVA: Místo "it.userId" voláme "UserCompanyAccess.userId"
        UserCompanyAccess.deleteWhere {
            UserCompanyAccess.userId eq EntityID(targetUserId, UsersSchema)
        }

        if (companyIds.isNotEmpty()) {
            UserCompanyAccess.batchInsert(companyIds) { cid ->
                this[UserCompanyAccess.userId] = EntityID(targetUserId, UsersSchema)
                this[UserCompanyAccess.companyId] = EntityID(cid, Companies)
            }
        }

        // ✅ OPRAVA: Místo "it.userId" voláme "UserCityAccess.userId"
        UserCityAccess.deleteWhere {
            UserCityAccess.userId eq EntityID(targetUserId, UsersSchema)
        }

        if (cities.isNotEmpty()) {
            UserCityAccess.batchInsert(cities) { city ->
                this[UserCityAccess.userId] = EntityID(targetUserId, UsersSchema)
                this[UserCityAccess.city] = city
            }
        }
    }
}