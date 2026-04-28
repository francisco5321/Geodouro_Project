package com.example.geodouro_backend.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

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
    val role: String,
    val authToken: String,
    val tokenType: String = "Bearer"
)

data class CurrentUserResponse(
    val userId: Int,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val displayName: String,
    val role: String
)

data class SignupRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val firstName: String,
    @field:NotBlank
    @field:Size(max = 120)
    val lastName: String,
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,
    @field:NotBlank
    @field:Size(max = 255)
    val username: String,
    @field:NotBlank
    @field:Size(min = 8, max = 255)
    val password: String
)

data class UpdateProfileRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val firstName: String,
    @field:NotBlank
    @field:Size(max = 120)
    val lastName: String,
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,
    @field:NotBlank
    @field:Size(max = 255)
    val username: String
)

data class ChangePasswordRequest(
    @field:NotBlank
    val currentPassword: String,
    @field:NotBlank
    @field:Size(min = 8, max = 255)
    val newPassword: String
)

data class UpdateUserRoleRequest(
    @field:NotBlank
    val role: String
)

data class UserSummaryResponse(
    val userId: Int,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val displayName: String,
    val role: String,
    val createdAt: String?
)
