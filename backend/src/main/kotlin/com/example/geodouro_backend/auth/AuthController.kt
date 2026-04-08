package com.example.geodouro_backend.auth

import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
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

    @GetMapping("/me")
    fun currentUser(
        @RequestHeader(name = "Authorization", required = false) authorizationHeader: String?
    ): CurrentUserResponse {
        val userId = authTokenService.resolveUserId(authorizationHeader)
            ?: throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Token de autenticacao em falta"
            )
        logger.info("GET /api/auth/me userId={}", userId)
        return authService.getCurrentUser(userId)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AuthController::class.java)
    }
}
