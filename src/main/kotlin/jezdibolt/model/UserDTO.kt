package jezdibolt.model

import kotlinx.serialization.Serializable

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

@Serializable
data class UserWithRightsDto(
    val user: UserDTO,
    val permissions: List<String>,       // ["VIEW_CARS", "EDIT_USERS"]
    val accessibleCompanyIds: List<Int>, // [1, 2] - Firmy, které smí vidět
    val accessibleCities: List<String>   // ["Prague"] - Města, která smí vidět
)

@Serializable
data class UpdatePermissionsRequest(
    val permissions: List<String>,
    val accessibleCompanyIds: List<Int>,
    val accessibleCities: List<String>
)