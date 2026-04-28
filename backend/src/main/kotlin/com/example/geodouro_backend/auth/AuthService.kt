package com.example.geodouro_backend.auth

import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

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
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida")

        return user.toCurrentUserResponse()
    }

    fun signup(request: SignupRequest): LoginResponse {
        val email = request.email.trim().lowercase()
        val username = request.username.trim()
        ensureUniqueLogin(email, username, null)

        val userId = jdbcTemplate.queryForObject(
            """
            INSERT INTO app_user (
                is_authenticated, guest_label, first_name, last_name, email, username, password_hash, role
            )
            VALUES (
                TRUE, :guestLabel, :firstName, :lastName, :email, :username, :passwordHash, 'user'
            )
            RETURNING user_id
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("guestLabel", "web-${Instant.now().toEpochMilli()}")
                .addValue("firstName", request.firstName.trim())
                .addValue("lastName", request.lastName.trim())
                .addValue("email", email)
                .addValue("username", username)
                .addValue("passwordHash", passwordEncoder.encode(request.password)),
            Int::class.java
        ) ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível criar a conta")

        val user = loadUser(userId)
        return user.toLoginResponse(authTokenService.createToken(user.userId))
    }

    fun updateProfile(userId: Int, request: UpdateProfileRequest): CurrentUserResponse {
        val email = request.email.trim().lowercase()
        val username = request.username.trim()
        ensureUniqueLogin(email, username, userId)

        jdbcTemplate.update(
            """
            UPDATE app_user
            SET first_name = :firstName,
                last_name = :lastName,
                email = :email,
                username = :username,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId AND is_authenticated = TRUE
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("firstName", request.firstName.trim())
                .addValue("lastName", request.lastName.trim())
                .addValue("email", email)
                .addValue("username", username)
        )

        return loadUser(userId).toCurrentUserResponse()
    }

    fun changePassword(userId: Int, request: ChangePasswordRequest): LoginResponse {
        val user = loadUser(userId)
        val storedHash = user.passwordHash?.trim().orEmpty()
        if (storedHash.isBlank() || !matchesPassword(request.currentPassword, storedHash)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Password atual invalida")
        }

        jdbcTemplate.update(
            """
            UPDATE app_user
            SET password_hash = :passwordHash,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId AND is_authenticated = TRUE
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("passwordHash", passwordEncoder.encode(request.newPassword))
        )

        val refreshedUser = loadUser(userId)
        return refreshedUser.toLoginResponse(authTokenService.createToken(refreshedUser.userId))
    }

    fun listUsers(requesterId: Int): List<UserSummaryResponse> {
        requireAdmin(requesterId)
        return jdbcTemplate.query(
            """
            SELECT user_id, username, email, first_name, last_name, password_hash, COALESCE(role, 'user') AS role, created_at
            FROM app_user
            WHERE is_authenticated = TRUE
            ORDER BY first_name ASC, last_name ASC, username ASC
            """.trimIndent(),
            userSummaryRowMapper
        )
    }

    fun updateUserRole(requesterId: Int, targetUserId: Int, request: UpdateUserRoleRequest): UserSummaryResponse {
        requireAdmin(requesterId)
        val role = request.role.trim().lowercase()
        if (role !in setOf("user", "admin")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Role invalida")
        }

        jdbcTemplate.update(
            """
            UPDATE app_user
            SET role = :role,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId AND is_authenticated = TRUE
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("userId", targetUserId)
                .addValue("role", role)
        )

        return jdbcTemplate.query(
            """
            SELECT user_id, username, email, first_name, last_name, password_hash, COALESCE(role, 'user') AS role, created_at
            FROM app_user
            WHERE is_authenticated = TRUE AND user_id = :userId
            LIMIT 1
            """.trimIndent(),
            MapSqlParameterSource("userId", targetUserId),
            userSummaryRowMapper
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Utilizador não encontrado")
    }

    private fun loadUser(userId: Int): AuthenticatedUserRecord {
        return jdbcTemplate.query(
            FIND_AUTHENTICATED_USER_BY_ID_SQL,
            MapSqlParameterSource("userId", userId),
            userRowMapper
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Utilizador não encontrado") //2
    }

    private fun ensureUniqueLogin(email: String, username: String, currentUserId: Int?) {
        val currentUserClause = if (currentUserId == null) "" else "AND user_id <> :currentUserId"
        val params = MapSqlParameterSource()
            .addValue("email", email)
            .addValue("username", username)
        if (currentUserId != null) {
            params.addValue("currentUserId", currentUserId)
        }

        val existingCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM app_user
            WHERE is_authenticated = TRUE
              AND (LOWER(email) = :email OR LOWER(username) = LOWER(:username))
              $currentUserClause
            """.trimIndent(),
            params,
            Int::class.java
        ) ?: 0

        if (existingCount > 0) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email ou username já está em uso")
        }
    }

    private fun requireAdmin(userId: Int) {
        val user = loadUser(userId)
        if (user.role != "admin") {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso reservado a administradores")
        }
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
        val passwordHash: String?,
        val role: String
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
                role = role,
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
                    .ifBlank { username },
                role = role
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
            passwordHash = rs.getString("password_hash"),
            role = rs.getString("role") ?: "user"
        )
    }

    private val userSummaryRowMapper = RowMapper { rs, _ ->
        val firstName = rs.getString("first_name")
        val lastName = rs.getString("last_name")
        val username = rs.getString("username")
        UserSummaryResponse(
            userId = rs.getInt("user_id"),
            username = username,
            email = rs.getString("email"),
            firstName = firstName,
            lastName = lastName,
            displayName = listOf(firstName, lastName).joinToString(" ").trim().ifBlank { username },
            role = rs.getString("role") ?: "user",
            createdAt = rs.getTimestamp("created_at")?.toInstant()?.toString()
        )
    }

    companion object {
        private const val USER_SELECT = """
            SELECT user_id, username, email, first_name, last_name, password_hash, COALESCE(role, 'user') AS role
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
