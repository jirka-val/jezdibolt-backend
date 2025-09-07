package jezdibolt.service

import org.mindrot.jbcrypt.BCrypt

object PasswordHelper {
    fun hash(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    fun verify(password: String, hashed: String): Boolean {
        return BCrypt.checkpw(password, hashed)
    }
}
