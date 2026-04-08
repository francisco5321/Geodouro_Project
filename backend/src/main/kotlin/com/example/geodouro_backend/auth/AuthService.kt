package com.example.geodouro_backend.auth

import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AuthService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    private val passwordEncoder = BCryptPasswordEncoder()

    fun login(request: LoginRequest): LoginResponse {
        val identifier = request.identifier.trim().lowercase()
        val password = request.password

        val user = jdbcTemplate.query(
            FIND_AUTHENTICATED_USER_SQL,
            MapSqlParameterSource("identifier", identifier),
            userRowMapper
        ).firstOrNull()
            ?: throw invalidCredentials()

        val storedHash = user.passwordHash?.trim().orEmpty()
        if (storedHash.isBlank() || !matchesPassword(password, storedHash)) {
            throw invalidCredentials()
        }

        return LoginResponse(
            userId = user.userId,
            username = user.username,
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            displayName = listOf(user.firstName, user.lastName)
                .joinToString(" ")
                .trim()
                .ifBlank { user.username }
        )
    }

    private fun matchesPassword(rawPassword: String, storedHash: String): Boolean {
        val normalizedHash = if (storedHash.startsWith("$2y$")) {
            "$2a$" + storedHash.removePrefix("$2y$")
        } else {
            storedHash
        }
        return runCatching {
            passwordEncoder.matches(rawPassword, normalizedHash)
        }.getOrDefault(false)
    }

    private fun invalidCredentials(): ResponseStatusException {
        return ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas")
    }

    private data class AuthenticatedUserRecord(
        val userId: Int,
        val username: String,
        val email: String,
        val firstName: String,
        val lastName: String,
        val passwordHash: String?
    )

    private val userRowMapper = RowMapper { rs, _ ->
        AuthenticatedUserRecord(
            userId = rs.getInt("user_id"),
            username = rs.getString("username"),
            email = rs.getString("email"),
            firstName = rs.getString("first_name"),
            lastName = rs.getString("last_name"),
            passwordHash = rs.getString("password_hash")
        )
    }

    companion object {
        private const val FIND_AUTHENTICATED_USER_SQL = """
            SELECT user_id, username, email, first_name, last_name, password_hash
            FROM app_user
            WHERE is_authenticated = TRUE
              AND (
                LOWER(username) = :identifier
                OR LOWER(email) = :identifier
              )
            LIMIT 1
        """
    }
}
