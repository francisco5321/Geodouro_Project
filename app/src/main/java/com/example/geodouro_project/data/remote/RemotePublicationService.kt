package com.example.geodouro_project.data.remote

import android.util.Log
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RemotePublicationService(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val config: RemoteDbConfig,
    private val currentIdentityProvider: () -> RemoteUserIdentity?
) {

    fun isConfigured(): Boolean = config.isConfigured()

    fun publishObservation(observation: ObservationEntity): Boolean {
        return publishObservationId(observation.id)
    }

    fun publishObservationId(deviceObservationId: String): Boolean {
        if (!isConfigured()) {
            return false
        }

        val payload = RemotePublishObservationPayload(
            deviceObservationId = deviceObservationId
        )

        val identity = currentIdentityProvider()
        val requestBuilder = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/api/publications")
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))

        identity?.authToken?.takeIf { it.isNotBlank() }?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d(TAG, "Publish response code=${response.code} body=$body")
                response.isSuccessful
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to publish observation $deviceObservationId", error)
        }.getOrDefault(false)
    }

    fun fetchPublications(): List<RemoteCommunityPublication> {
        if (!isConfigured()) {
            return emptyList()
        }

        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/api/publications")
            .get()
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Fetch publications failed code=${response.code}")
                    return@use emptyList()
                }

                val body = response.body?.string().orEmpty()
                gson.fromJson(body, Array<RemotePublicationResponse>::class.java)
                    ?.map { publication ->
                        RemoteCommunityPublication(
                            publicationId = publication.publicationId,
                            deviceObservationId = publication.deviceObservationId,
                            scientificName = publication.scientificName,
                            commonName = publication.commonName,
                            userDisplayName = publication.userDisplayName,
                            latitude = publication.latitude,
                            longitude = publication.longitude,
                            publishedAt = publication.publishedAt,
                            imageUrl = publication.imagePath
                                ?.takeIf { it.isNotBlank() }
                                ?.let { path -> config.baseUrl.trimEnd('/') + "/uploads/" + path.trimStart('/') }
                        )
                    }
                    .orEmpty()
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to fetch publications", error)
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val TAG = "RemotePublication"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

data class RemoteCommunityPublication(
    val publicationId: Int,
    val deviceObservationId: String,
    val scientificName: String,
    val commonName: String?,
    val userDisplayName: String,
    val latitude: Double?,
    val longitude: Double?,
    val publishedAt: String,
    val imageUrl: String?
)

private data class RemotePublishObservationPayload(
    val deviceObservationId: String
)

private data class RemotePublicationResponse(
    val publicationId: Int,
    val deviceObservationId: String,
    val scientificName: String,
    val commonName: String?,
    val userDisplayName: String,
    val latitude: Double?,
    val longitude: Double?,
    val publishedAt: String,
    @SerializedName("imagePath")
    val imagePath: String?
)
