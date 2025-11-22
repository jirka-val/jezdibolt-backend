package jezdibolt.model

import kotlinx.serialization.Serializable

// To co vracíme na FE (bez hesla)
@Serializable
data class UserDTO(
    val id: Int? = null,
    val name: String,
    val email: String,
    val contact: String?,
    val role: String,
    val companyId: Int? = null,
    val companyName: String? = null
)

// 🆕 To co posílá FE při vytváření (s heslem)
@Serializable
data class CreateUserRequest(
    val name: String,
    val email: String,
    val password: String, // 🔐 Heslo je povinné
    val contact: String? = null,
    val role: String,     // např. "admin", "driver"
    val companyId: Int? = null
)

// 🆕 To co posílá FE při úpravě (heslo je volitelné)
@Serializable
data class UpdateUserRequest(
    val name: String,
    val email: String,
    val contact: String? = null,
    val role: String,
    val companyId: Int? = null,
    val password: String? = null // 🔐 Pokud je vyplněné, změníme ho
)

// ... (UserWithRightsDto a UpdatePermissionsRequest nechej jak jsou) ...
@Serializable
data class UserWithRightsDto(
    val user: UserDTO,
    val permissions: List<String>,
    val accessibleCompanyIds: List<Int>,
    val accessibleCities: List<String>
)

@Serializable
data class UpdatePermissionsRequest(
    val permissions: List<String>,
    val accessibleCompanyIds: List<Int>,
    val accessibleCities: List<String>
)