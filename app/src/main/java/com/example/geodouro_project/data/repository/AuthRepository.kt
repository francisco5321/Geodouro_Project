package com.example.geodouro_project.data.repository

import com.example.geodouro_project.data.local.AuthSessionStorage
import com.example.geodouro_project.data.remote.RemoteAuthService
import com.example.geodouro_project.data.remote.RemoteUserIdentity
import com.example.geodouro_project.domain.model.SessionState
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthRepository(
    private val sessionStorage: AuthSessionStorage,
    private val remoteAuthService: RemoteAuthService,
    private val fallbackAuthenticatedUserId: Int,
    private val guestLabelPrefix: String
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Loading)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    init {
        repositoryScope.launch {
            sessionStorage.sessionState.collect { persistedState ->
                _sessionState.value = persistedState
            }
        }
    }

    suspend fun login(identifier: String, password: String): Result<Unit> {
        return runCatching {
            val remoteUser = withContext(Dispatchers.IO) {
                remoteAuthService.login(
                    identifier = identifier.trim(),
                    password = password
                )
            }
            val session = SessionState.Authenticated(
                userId = remoteUser.userId.takeIf { it > 0 } ?: fallbackAuthenticatedUserId.takeIf { it > 0 },
                username = remoteUser.username,
                email = remoteUser.email,
                displayName = remoteUser.displayName
            )
            sessionStorage.saveAuthenticatedSession(session)
        }
    }

    suspend fun continueAsGuest() {
        val currentGuest = _sessionState.value as? SessionState.Guest
        val session = currentGuest ?: SessionState.Guest(
            guestLabel = buildGuestLabel(),
            displayName = DEFAULT_GUEST_DISPLAY_NAME
        )
        sessionStorage.saveGuestSession(session)
    }

    suspend fun logout() {
        sessionStorage.clearSession()
    }

    fun currentRemoteIdentity(): RemoteUserIdentity? {
        return when (val state = _sessionState.value) {
            is SessionState.Authenticated -> RemoteUserIdentity(userId = state.userId, guestLabel = null)
            is SessionState.Guest -> RemoteUserIdentity(userId = null, guestLabel = state.guestLabel)
            SessionState.Loading,
            SessionState.LoggedOut -> null
        }
    }

    private fun buildGuestLabel(): String {
        val normalizedPrefix = guestLabelPrefix
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-')
            .ifBlank { "guest" }

        return "$normalizedPrefix-${UUID.randomUUID().toString().take(8)}"
    }

    companion object {
        private const val DEFAULT_GUEST_DISPLAY_NAME = "Convidado"
    }
}
