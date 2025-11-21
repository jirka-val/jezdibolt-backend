package jezdibolt.model

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object PermissionDefinitions : Table("permission_definitions") {
    val code = varchar("code", 50)
    val label = varchar("label", 100)
    val category = varchar("category", 50)

    override val primaryKey = PrimaryKey(code)
}

object UserPermissions : Table("user_permissions") {
    val userId = reference("user_id", UsersSchema, onDelete = ReferenceOption.CASCADE)
    val permissionCode = reference("permission_code", PermissionDefinitions.code, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(userId, permissionCode)
}

object UserCompanyAccess : Table("user_company_access") {
    val userId = reference("user_id", UsersSchema, onDelete = ReferenceOption.CASCADE)
    val companyId = reference("company_id", Companies, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(userId, companyId)
}

object UserCityAccess : Table("user_city_access") {
    val userId = reference("user_id", UsersSchema, onDelete = ReferenceOption.CASCADE)
    val city = varchar("city", 100)

    override val primaryKey = PrimaryKey(userId, city)
}