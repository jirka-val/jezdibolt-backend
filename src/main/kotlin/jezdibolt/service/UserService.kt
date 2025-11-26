package jezdibolt.service

import jezdibolt.model.*
import jezdibolt.repository.UserRepository
import jezdibolt.repository.UserRightsRepository

class UserService(
    private val userRepository: UserRepository = UserRepository(),
    private val rightsRepository: UserRightsRepository = UserRightsRepository()
) {

    /**
     * Vrátí seznam uživatelů, které má `requesterId` právo vidět.
     */
    fun getAllUsers(requesterId: Int, requesterRole: String): List<UserDTO> {
        // 1. Pokud je OWNER, vidí vše (beze změny)
        if (requesterRole == "owner") {
            return userRepository.getAll()
        }

        // 2. Zjistíme, jaký má requester rozsah (Scope)
        val allowedCities = rightsRepository.getAllowedCities(requesterId)
        val allowedCompanies = rightsRepository.getAllowedCompanies(requesterId)

        // 🆕 Zjistíme, jestli je to admin (má privilegovaný pohled na ostatní adminy)
        val isPrivilegedViewer = requesterRole == "admin"

        // 3. Pokud nemá žádný scope A NENÍ ADMIN, nevidí nic
        if (allowedCities.isEmpty() && allowedCompanies.isEmpty() && !isPrivilegedViewer) {
            return emptyList()
        }

        // 4. Filtrovaný select - 🛠️ ZDE BYLA CHYBA, PŘIDÁVÁME TŘETÍ PARAMETR
        return userRepository.getAllFiltered(
            allowedCities,
            allowedCompanies,
            includePrivileged = isPrivilegedViewer
        )
    }

    // 🆕 Create s hashováním
    fun createUser(req: CreateUserRequest): UserDTO {
        val hash = PasswordHelper.hash(req.password)

        val newUser = userRepository.create(
            name = req.name,
            email = req.email,
            passwordHash = hash,
            contact = req.contact,
            role = req.role,
            companyId = req.companyId
        )

        // Pokud zakládáme admina, dáme mu defaultně právo vidět dashboard
        if (req.role == "admin" && newUser.id != null) {
            rightsRepository.updatePermissions(newUser.id, listOf("VIEW_DASHBOARD"))
        }

        return newUser
    }

    // 🆕 Update s volitelným hashováním
    fun updateUser(id: Int, req: UpdateUserRequest): Boolean {
        val hash = if (!req.password.isNullOrBlank()) {
            PasswordHelper.hash(req.password)
        } else {
            null
        }

        return userRepository.update(
            id = id,
            name = req.name,
            email = req.email,
            contact = req.contact,
            role = req.role,
            companyId = req.companyId,
            passwordHash = hash
        )
    }

    fun hasPermission(userId: Int, permissionCode: String): Boolean {
        val role = userRepository.getRole(userId)
        if (role == "owner") return true
        return rightsRepository.hasPermission(userId, permissionCode)
    }

    fun getUserWithRights(userId: Int): UserWithRightsDto? {
        val user = userRepository.getById(userId) ?: return null

        val perms = rightsRepository.getUserPermissions(userId)
        val companies = rightsRepository.getAllowedCompanies(userId)
        val cities = rightsRepository.getAllowedCities(userId)

        return UserWithRightsDto(
            user = user,
            permissions = perms,
            accessibleCompanyIds = companies,
            accessibleCities = cities
        )
    }

    fun updateUserPermissions(userId: Int, req: UpdatePermissionsRequest) {
        rightsRepository.updatePermissions(userId, req.permissions)
        rightsRepository.updateAccess(userId, req.accessibleCompanyIds, req.accessibleCities)
    }

    fun getAllowedCities(userId: Int): List<String> {
        return rightsRepository.getAllowedCities(userId)
    }

    fun getAllowedCompanies(userId: Int): List<Int> {
        return rightsRepository.getAllowedCompanies(userId)
    }
}