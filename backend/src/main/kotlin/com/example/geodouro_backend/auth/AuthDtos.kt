package com.example.geodouro_backend.auth

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "identifier is required")
    val identifier: String,
    @field:NotBlank(message = "password is required")
    val password: String
)

data class LoginResponse(
    val userId: Int,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val displayName: String,
    val authToken: String,
    val tokenType: String = "Bearer"
)

data class CurrentUserResponse(
    val userId: Int,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val displayName: String
)
