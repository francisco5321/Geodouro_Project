package com.example.geodouro_project.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.geodouro_project.domain.model.SessionState
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.authSessionDataStore by preferencesDataStore(name = "auth_session")

class AuthSessionStorage(
    private val appContext: Context
) {
    val sessionState: Flow<SessionState> = appContext.authSessionDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map(::preferencesToSessionState)

    suspend fun saveAuthenticatedSession(session: SessionState.Authenticated) {
        appContext.authSessionDataStore.edit { preferences ->
            preferences[SESSION_MODE_KEY] = MODE_AUTHENTICATED
            preferences[USER_ID_KEY] = session.userId ?: NO_USER_ID
            preferences[USERNAME_KEY] = session.username
            preferences[EMAIL_KEY] = session.email.orEmpty()
            preferences[DISPLAY_NAME_KEY] = session.displayName
            if (session.authToken.isNullOrBlank()) {
                preferences.remove(AUTH_TOKEN_KEY)
            } else {
                preferences[AUTH_TOKEN_KEY] = session.authToken
            }
            preferences.remove(GUEST_LABEL_KEY)
        }
    }

    suspend fun saveGuestSession(session: SessionState.Guest) {
        appContext.authSessionDataStore.edit { preferences ->
            preferences[SESSION_MODE_KEY] = MODE_GUEST
            preferences[DISPLAY_NAME_KEY] = session.displayName
            preferences[GUEST_LABEL_KEY] = session.guestLabel
            preferences.remove(USER_ID_KEY)
            preferences.remove(USERNAME_KEY)
            preferences.remove(EMAIL_KEY)
            preferences.remove(AUTH_TOKEN_KEY)
        }
    }

    suspend fun clearSession() {
        appContext.authSessionDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private fun preferencesToSessionState(preferences: Preferences): SessionState {
        return when (preferences[SESSION_MODE_KEY]) {
            MODE_AUTHENTICATED -> {
                val username = preferences[USERNAME_KEY].orEmpty().trim()
                val displayName = preferences[DISPLAY_NAME_KEY].orEmpty().trim()
                if (username.isBlank() || displayName.isBlank()) {
                    SessionState.LoggedOut
                } else {
                    SessionState.Authenticated(
                        userId = preferences[USER_ID_KEY]?.takeIf { it != NO_USER_ID },
                        username = username,
                        email = preferences[EMAIL_KEY]?.trim()?.takeIf { it.isNotBlank() },
                        displayName = displayName,
                        authToken = preferences[AUTH_TOKEN_KEY]?.trim()?.takeIf { it.isNotBlank() }
                    )
                }
            }

            MODE_GUEST -> {
                val guestLabel = preferences[GUEST_LABEL_KEY].orEmpty().trim()
                if (guestLabel.isBlank()) {
                    SessionState.LoggedOut
                } else {
                    SessionState.Guest(
                        guestLabel = guestLabel,
                        displayName = preferences[DISPLAY_NAME_KEY]
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: DEFAULT_GUEST_DISPLAY_NAME
                    )
                }
            }

            else -> SessionState.LoggedOut
        }
    }

    private companion object {
        const val DEFAULT_GUEST_DISPLAY_NAME = "Convidado"
        const val MODE_AUTHENTICATED = "authenticated"
        const val MODE_GUEST = "guest"
        const val NO_USER_ID = -1

        val SESSION_MODE_KEY = stringPreferencesKey("session_mode")
        val USER_ID_KEY = intPreferencesKey("user_id")
        val USERNAME_KEY = stringPreferencesKey("username")
        val EMAIL_KEY = stringPreferencesKey("email")
        val DISPLAY_NAME_KEY = stringPreferencesKey("display_name")
        val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
        val GUEST_LABEL_KEY = stringPreferencesKey("guest_label")
    }
}
