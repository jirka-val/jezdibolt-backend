package jezdibolt.model

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(UsersSchema)

    var name by UsersSchema.name
    var email by UsersSchema.email
    var contact by UsersSchema.contact
    var role by UsersSchema.role
    var passwordHash by UsersSchema.passwordHash
}
