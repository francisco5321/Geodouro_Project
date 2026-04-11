package com.example.geodouro_project.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RemoteVisitTargetService(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val config: RemoteDbConfig
) {
    fun isConfigured(): Boolean = config.isConfigured()

    fun fetchVisitTargets(userId: Int?, authToken: String?): List<RemoteVisitTarget> {
        if (!isConfigured()) return emptyList()

        val url = buildVisitTargetsUrl(userId)
        Log.d(TAG, "Fetching visit targets url=$url hasToken=${!authToken.isNullOrBlank()}")
        val request = authorizedRequestBuilder(url, authToken)
            .get()
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.w(TAG, "Fetch visit targets failed code=${response.code} body=$body")
                throw IllegalStateException("Backend devolveu ${response.code} ao carregar Quero visitar.")
            }

            val targets = gson.fromJson(body, Array<RemoteVisitTargetResponse>::class.java)
                ?.map { it.toDomain() }
                .orEmpty()
            Log.d(TAG, "Fetched ${targets.size} visit targets for userId=$userId")
            targets
        }
    }

    fun deleteVisitTarget(savedVisitTargetId: Int, userId: Int?, authToken: String?): Boolean {
        if (!isConfigured()) return false

        val request = authorizedRequestBuilder(buildVisitTargetUrl(savedVisitTargetId, userId), authToken)
            .delete()
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Delete visit target failed code=${response.code} id=$savedVisitTargetId body=$body")
                    return@use false
                }
                true
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to delete visit target $savedVisitTargetId", error)
        }.getOrDefault(false)
    }

    fun toggleVisitTarget(
        targetType: String,
        targetId: Int,
        userId: Int?,
        authToken: String?
    ): RemoteToggleVisitTarget? {
        if (!isConfigured()) return null

        val payload = gson.toJson(
            mapOf(
                "targetType" to targetType,
                "targetId" to targetId
            )
        ).toRequestBody(JSON_MEDIA_TYPE)

        val request = authorizedRequestBuilder(buildVisitTargetsToggleUrl(userId), authToken)
            .post(payload)
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Toggle visit target failed code=${response.code} type=$targetType id=$targetId body=$body")
                    return@use null
                }

                gson.fromJson(body, RemoteToggleVisitTargetResponse::class.java)?.toDomain()
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to toggle visit target $targetType/$targetId", error)
        }.getOrNull()
    }

    private fun authorizedRequestBuilder(url: String, authToken: String?): Request.Builder {
        return Request.Builder()
            .url(url)
            .apply {
                authToken?.takeIf { it.isNotBlank() }?.let { token ->
                    header("Authorization", "Bearer $token")
                }
            }
    }

    private fun buildVisitTargetsUrl(userId: Int?): String {
        val base = config.baseUrl.trimEnd('/') + "/api/visit-targets"
        return if (userId != null) "$base?userId=$userId" else base
    }

    private fun buildVisitTargetsToggleUrl(userId: Int?): String {
        val base = config.baseUrl.trimEnd('/') + "/api/visit-targets/toggle"
        return if (userId != null) "$base?userId=$userId" else base
    }

    private fun buildVisitTargetUrl(savedVisitTargetId: Int, userId: Int?): String {
        val base = config.baseUrl.trimEnd('/') + "/api/visit-targets/$savedVisitTargetId"
        return if (userId != null) "$base?userId=$userId" else base
    }

    companion object {
        private const val TAG = "RemoteVisitTarget"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

data class RemoteVisitTarget(
    val savedVisitTargetId: Int,
    val userId: Int,
    val targetType: String,
    val title: String,
    val subtitle: String?,
    val notes: String?,
    val observationId: Int?,
    val plantSpeciesId: Int?,
    val publicationId: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val createdAt: String?
)

data class RemoteToggleVisitTarget(
    val success: Boolean,
    val saved: Boolean,
    val message: String,
    val target: RemoteVisitTarget?
)

private data class RemoteVisitTargetResponse(
    @SerializedName("savedVisitTargetId") val savedVisitTargetId: Int,
    @SerializedName("userId") val userId: Int,
    @SerializedName("targetType") val targetType: String,
    @SerializedName("title") val title: String,
    @SerializedName("subtitle") val subtitle: String?,
    @SerializedName("notes") val notes: String?,
    @SerializedName("observationId") val observationId: Int?,
    @SerializedName("plantSpeciesId") val plantSpeciesId: Int?,
    @SerializedName("publicationId") val publicationId: Int?,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("createdAt") val createdAt: String?
) {
    fun toDomain(): RemoteVisitTarget = RemoteVisitTarget(
        savedVisitTargetId = savedVisitTargetId,
        userId = userId,
        targetType = targetType,
        title = title,
        subtitle = subtitle,
        notes = notes,
        observationId = observationId,
        plantSpeciesId = plantSpeciesId,
        publicationId = publicationId,
        latitude = latitude,
        longitude = longitude,
        createdAt = createdAt
    )
}

private data class RemoteToggleVisitTargetResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("saved") val saved: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("target") val target: RemoteVisitTargetResponse?
) {
    fun toDomain(): RemoteToggleVisitTarget = RemoteToggleVisitTarget(
        success = success,
        saved = saved,
        message = message,
        target = target?.toDomain()
    )
}

