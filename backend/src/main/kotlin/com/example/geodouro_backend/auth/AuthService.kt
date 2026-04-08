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
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val authTokenService: AuthTokenService
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

        return user.toLoginResponse(authTokenService.createToken(user.userId))
    }

    fun getCurrentUser(userId: Int): CurrentUserResponse {
        val user = jdbcTemplate.query(
            FIND_AUTHENTICATED_USER_BY_ID_SQL,
            MapSqlParameterSource("userId", userId),
            userRowMapper
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessao invalida")

        return user.toCurrentUserResponse()
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
    ) {
        fun toLoginResponse(authToken: String): LoginResponse {
            return LoginResponse(
                userId = userId,
                username = username,
                email = email,
                firstName = firstName,
                lastName = lastName,
                displayName = listOf(firstName, lastName)
                    .joinToString(" ")
                    .trim()
                    .ifBlank { username },
                authToken = authToken
            )
        }

        fun toCurrentUserResponse(): CurrentUserResponse {
            return CurrentUserResponse(
                userId = userId,
                username = username,
                email = email,
                firstName = firstName,
                lastName = lastName,
                displayName = listOf(firstName, lastName)
                    .joinToString(" ")
                    .trim()
                    .ifBlank { username }
            )
        }
    }

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
        private const val USER_SELECT = """
            SELECT user_id, username, email, first_name, last_name, password_hash
            FROM app_user
            WHERE is_authenticated = TRUE
        """

        private const val FIND_AUTHENTICATED_USER_SQL = USER_SELECT + """
              AND (
                LOWER(username) = :identifier
                OR LOWER(email) = :identifier
              )
            LIMIT 1
        """

        private const val FIND_AUTHENTICATED_USER_BY_ID_SQL = USER_SELECT + """
              AND user_id = :userId
            LIMIT 1
        """
    }
}
