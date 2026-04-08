package com.example.geodouro_project.domain.model

sealed interface SessionState {
    data object Loading : SessionState

    data object LoggedOut : SessionState

    data class Authenticated(
        val userId: Int?,
        val username: String,
        val email: String?,
        val displayName: String
    ) : SessionState

    data class Guest(
        val guestLabel: String,
        val displayName: String
    ) : SessionState
}
