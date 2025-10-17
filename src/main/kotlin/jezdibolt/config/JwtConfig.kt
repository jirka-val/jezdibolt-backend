package jezdibolt.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.github.cdimascio.dotenv.dotenv
import java.util.*

object JwtConfig {
    private val dotenv = dotenv { ignoreIfMissing = true }
    private val secret = dotenv["JWT_SECRET"] ?: "default-secret"
    private val issuer = dotenv["JWT_ISSUER"] ?: "jezdibolt"
    private val audience = dotenv["JWT_AUDIENCE"] ?: "jezdibolt-users"
    private val algorithm = Algorithm.HMAC256(secret)

    fun generateToken(
        userId: Int,
        email: String,
        role: String,
        companyId: Int?
    ): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withClaim("role", role)
            .withClaim("companyId", companyId)
            .withExpiresAt(Date(System.currentTimeMillis() + 1000L * 60L * 60L * 24L)) // 24 h
            .sign(algorithm)
    }
}
