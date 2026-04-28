package com.example.geodouro_backend.auth

import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val authTokenService: AuthTokenService
) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): LoginResponse {
        logger.info("POST /api/auth/login identifier={}", request.identifier)
        return authService.login(request)
    }

    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: SignupRequest): LoginResponse {
        logger.info("POST /api/auth/signup username={}", request.username)
        return authService.signup(request)
    }

    @GetMapping("/me")
    fun currentUser(
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?
    ): CurrentUserResponse {
        val userId = authTokenService.resolveUserId(authorizationHeader)
            ?: throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Token de autenticação em falta"
            )
        logger.info("GET /api/auth/me userId={}", userId)
        return authService.getCurrentUser(userId)
    }

    @PatchMapping("/me")
    fun updateProfile(
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?,
        @Valid @RequestBody request: UpdateProfileRequest
    ): CurrentUserResponse {
        val userId = requireUserId(authorizationHeader)
        logger.info("PATCH /api/auth/me userId={}", userId)
        return authService.updateProfile(userId, request)
    }

    @PatchMapping("/me/password")
    fun changePassword(
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?,
        @Valid @RequestBody request: ChangePasswordRequest
    ): LoginResponse {
        val userId = requireUserId(authorizationHeader)
        logger.info("PATCH /api/auth/me/password userId={}", userId)
        return authService.changePassword(userId, request)
    }

    @GetMapping("/users")
    fun listUsers(
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?
    ): List<UserSummaryResponse> {
        val userId = requireUserId(authorizationHeader)
        logger.info("GET /api/auth/users requesterId={}", userId)
        return authService.listUsers(userId)
    }

    @PatchMapping("/users/{userId}/role")
    fun updateUserRole(
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?,
        @org.springframework.web.bind.annotation.PathVariable userId: Int,
        @Valid @RequestBody request: UpdateUserRoleRequest
    ): UserSummaryResponse {
        val requesterId = requireUserId(authorizationHeader)
        logger.info("PATCH /api/auth/users/{}/role requesterId={}", userId, requesterId)
        return authService.updateUserRole(requesterId, userId, request)
    }

    private fun requireUserId(authorizationHeader: String?): Int {
        return authTokenService.resolveUserId(authorizationHeader)
            ?: throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Token de autenticação em falta"
            )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AuthController::class.java)
    }
}
