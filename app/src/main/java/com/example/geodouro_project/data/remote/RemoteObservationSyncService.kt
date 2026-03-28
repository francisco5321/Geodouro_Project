package com.example.geodouro_project.data.remote

import android.util.Log
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.example.geodouro_project.data.remote.model.RemoteObservationPayload
import com.example.geodouro_project.domain.model.ObservationSyncStatus
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RemoteObservationSyncService(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val config: RemoteDbConfig
) {

    fun isConfigured(): Boolean = config.isConfigured()

    fun uploadObservation(observation: ObservationEntity, syncAttemptAt: Long): Boolean {
        if (!isConfigured()) {
            Log.w(TAG, "Remote backend is not configured. baseUrl='${config.baseUrl}'")
            return false
        }

        val configuredUserId = config.defaultUserId.takeIf { it > 0 }
        val payload = RemoteObservationPayload(
            deviceObservationId = observation.id,
            userId = configuredUserId,
            guestLabel = if (configuredUserId == null) config.guestLabel else null,
            imageUri = observation.imageUri,
            capturedAt = observation.capturedAt,
            predictedScientificName = observation.predictedSpecies,
            confidence = observation.confidence,
            enrichedScientificName = observation.enrichedScientificName,
            enrichedCommonName = observation.enrichedCommonName,
            enrichedFamily = observation.enrichedFamily,
            enrichedWikipediaUrl = observation.enrichedWikipediaUrl,
            enrichedPhotoUrl = observation.enrichedPhotoUrl,
            latitude = observation.latitude,
            longitude = observation.longitude,
            syncStatus = ObservationSyncStatus.SYNCED.name,
            lastSyncAttemptAt = syncAttemptAt
        )

        val url = buildObservationUrl()
        Log.d(TAG, "Uploading observation to $url payload=${gson.toJson(payload)}")
        val request = Request.Builder()
            .url(url)
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                Log.d(TAG, "Backend response code=${response.code} body=$responseBody")
                response.isSuccessful
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to upload observation to backend", error)
        }.getOrDefault(false)
    }

    private fun buildObservationUrl(): String {
        return config.baseUrl.trimEnd('/') + "/api/observations"
    }

    companion object {
        private const val TAG = "RemoteObservationSync"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
