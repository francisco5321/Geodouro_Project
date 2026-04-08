package com.example.geodouro_project.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RemoteAuthService(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val config: RemoteDbConfig
) {
    fun isConfigured(): Boolean = config.isConfigured()

    fun login(identifier: String, password: String): RemoteLoginResult {
        if (!isConfigured()) {
            throw IllegalStateException("Backend de autenticacao indisponivel.")
        }

        val requestBody = gson.toJson(
            RemoteLoginRequest(
                identifier = identifier.trim(),
                password = password
            )
        ).toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/api/auth/login")
            .post(requestBody)
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> gson.fromJson(body, RemoteLoginResponse::class.java).toDomain()
                    response.code == 401 -> throw IllegalArgumentException("Credenciais invalidas.")
                    else -> {
                        Log.w(TAG, "Login failed code=${response.code} body=$body")
                        throw IllegalStateException("Nao foi possivel autenticar de momento.")
                    }
                }
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to login remotely", error)
        }.getOrElse { error ->
            throw error
        }
    }

    private fun RemoteLoginResponse.toDomain(): RemoteLoginResult {
        return RemoteLoginResult(
            userId = userId,
            username = username,
            email = email,
            firstName = firstName,
            lastName = lastName,
            displayName = displayName
        )
    }

    companion object {
        private const val TAG = "RemoteAuthService"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

data class RemoteLoginResult(
    val userId: Int,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val displayName: String
)

private data class RemoteLoginRequest(
    val identifier: String,
    val password: String
)

private data class RemoteLoginResponse(
    @SerializedName("userId")
    val userId: Int,
    @SerializedName("username")
    val username: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("firstName")
    val firstName: String,
    @SerializedName("lastName")
    val lastName: String,
    @SerializedName("displayName")
    val displayName: String
)
