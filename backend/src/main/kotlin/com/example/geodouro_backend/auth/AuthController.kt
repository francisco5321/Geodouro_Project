package com.example.geodouro_backend.auth

import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): LoginResponse {
        logger.info("POST /api/auth/login identifier={}", request.identifier)
        return authService.login(request)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AuthController::class.java)
    }
}
