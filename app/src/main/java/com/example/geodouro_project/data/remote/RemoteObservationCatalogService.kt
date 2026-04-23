package com.example.geodouro_project.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RemoteObservationCatalogService(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val config: RemoteDbConfig,
    private val currentIdentityProvider: () -> RemoteUserIdentity?
) {

    fun isConfigured(): Boolean = config.isConfigured()

    fun fetchObservations(): List<RemoteObservationDetail> {
        if (!isConfigured()) return emptyList()
        val identity = currentIdentityProvider() ?: fallbackIdentity() ?: return emptyList()

        val requestBuilder = Request.Builder()
            .url(buildObservationListUrl(identity))
            .get()

        identity.authToken?.takeIf { it.isNotBlank() }?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string().orEmpty()
                gson.fromJson(body, Array<RemoteObservationDetailResponse>::class.java)
                    ?.map { it.toDomain() }
                    .orEmpty()
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to fetch observations", error)
        }.getOrDefault(emptyList())
    }

    fun fetchObservationDetail(deviceObservationId: String): RemoteObservationDetail? {
        if (!isConfigured()) return null
        val identity = currentIdentityProvider() ?: fallbackIdentity()
        val requestBuilder = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/api/observations/" + deviceObservationId)
            .get()

        identity?.authToken?.takeIf { it.isNotBlank() }?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                gson.fromJson(response.body?.string().orEmpty(), RemoteObservationDetailResponse::class.java)
                    ?.toDomain()
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to fetch observation detail $deviceObservationId", error)
        }.getOrNull()
    }

    fun updateObservationMetadata(
        deviceObservationId: String,
        scientificName: String,
        commonName: String,
        family: String
    ): Boolean {
        if (!isConfigured()) return false
        val identity = currentIdentityProvider() ?: fallbackIdentity()
        val body = gson.toJson(
            mapOf(
                "scientificName" to scientificName,
                "commonName" to commonName,
                "family" to family
            )
        ).toRequestBody(JSON_MEDIA_TYPE)
        val requestBuilder = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/api/observations/" + deviceObservationId)
            .patch(body)

        identity?.authToken?.takeIf { it.isNotBlank() }?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()
        return runCatching {
            httpClient.newCall(request).execute().use { response -> response.isSuccessful }
        }.onFailure { error ->
            Log.e(TAG, "Failed to update observation metadata $deviceObservationId", error)
        }.getOrDefault(false)
    }

    private fun buildObservationListUrl(identity: RemoteUserIdentity): String {
        val base = config.baseUrl.trimEnd('/') + "/api/observations"
        return when {
            !identity.authToken.isNullOrBlank() -> base
            identity.userId != null -> "$base?userId=${identity.userId}"
            !identity.guestLabel.isNullOrBlank() -> "$base?guestLabel=${identity.guestLabel}"
            else -> base
        }
    }

    private fun RemoteObservationDetailResponse.toDomain(): RemoteObservationDetail {
        return RemoteObservationDetail(
            deviceObservationId = deviceObservationId,
            userId = userId,
            scientificName = scientificName,
            commonName = commonName,
            family = family,
            wikipediaUrl = wikipediaUrl,
            photoUrl = photoUrl?.let(::resolveImageUrl),
            imageUrls = imagePaths.map(::resolveImageUrl),
            capturedAt = capturedAt ?: 0L,
            confidence = confidence ?: 0f,
            latitude = latitude,
            longitude = longitude,
            notes = notes,
            syncStatus = syncStatus,
            isPublished = isPublished
        )
    }

    private fun resolveImageUrl(path: String): String {
        val normalizedPath = path.trim()
        return if (normalizedPath.startsWith("http://") || normalizedPath.startsWith("https://")) {
            normalizedPath
        } else {
            config.baseUrl.trimEnd('/') + "/uploads/" + normalizedPath.trimStart('/')
        }
    }

    private fun fallbackIdentity(): RemoteUserIdentity? {
        return when {
            config.defaultUserId > 0 -> RemoteUserIdentity(userId = config.defaultUserId, guestLabel = null, authToken = null)
            config.guestLabel.isNotBlank() -> RemoteUserIdentity(userId = null, guestLabel = config.guestLabel, authToken = null)
            else -> null
        }
    }

    companion object {
        private const val TAG = "RemoteObservationCatalog"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

data class RemoteObservationDetail(
    val deviceObservationId: String,
    val userId: Int,
    val scientificName: String,
    val commonName: String?,
    val family: String?,
    val wikipediaUrl: String?,
    val photoUrl: String?,
    val imageUrls: List<String>,
    val capturedAt: Long,
    val confidence: Float,
    val latitude: Double?,
    val longitude: Double?,
    val notes: String?,
    val syncStatus: String,
    val isPublished: Boolean
)

private data class RemoteObservationDetailResponse(
    @SerializedName("deviceObservationId")
    val deviceObservationId: String,
    @SerializedName("userId")
    val userId: Int,
    @SerializedName("scientificName")
    val scientificName: String,
    @SerializedName("commonName")
    val commonName: String?,
    @SerializedName("family")
    val family: String?,
    @SerializedName("wikipediaUrl")
    val wikipediaUrl: String?,
    @SerializedName("photoUrl")
    val photoUrl: String?,
    @SerializedName("imagePaths")
    val imagePaths: List<String>,
    @SerializedName("capturedAt")
    val capturedAt: Long?,
    @SerializedName("confidence")
    val confidence: Float?,
    @SerializedName("latitude")
    val latitude: Double?,
    @SerializedName("longitude")
    val longitude: Double?,
    @SerializedName("notes")
    val notes: String?,
    @SerializedName("syncStatus")
    val syncStatus: String,
    @SerializedName("isPublished")
    val isPublished: Boolean
)
