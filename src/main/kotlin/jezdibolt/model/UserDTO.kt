package jezdibolt.model

import kotlinx.serialization.Serializable

@Serializable
data class UserDTO(
    val id: Int? = null,
    val name: String,
    val email: String,
    val contact: String?,
    val role: String
)
