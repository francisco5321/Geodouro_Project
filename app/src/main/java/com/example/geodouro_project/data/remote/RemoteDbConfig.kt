package com.example.geodouro_project.data.remote

data class RemoteDbConfig(
    val baseUrl: String,
    val guestLabel: String,
    val defaultUserId: Int
) {
    fun isConfigured(): Boolean {
        return baseUrl.isNotBlank()
    }
}

data class RemoteUserIdentity(
    val userId: Int?,
    val guestLabel: String?,
    val authToken: String? = null
)
