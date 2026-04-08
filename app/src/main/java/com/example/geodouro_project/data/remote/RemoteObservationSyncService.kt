package com.example.geodouro_project.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.example.geodouro_project.data.remote.model.RemoteObservationPayload
import com.example.geodouro_project.domain.model.ObservationSyncStatus
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileInputStream

class RemoteObservationSyncService(
    private val appContext: Context,
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val config: RemoteDbConfig,
    private val currentIdentityProvider: () -> RemoteUserIdentity?
) {

    fun isConfigured(): Boolean = config.isConfigured()

    fun uploadObservation(observation: ObservationEntity, syncAttemptAt: Long): Boolean {
        if (!isConfigured()) {
            Log.w(TAG, "Remote backend is not configured. baseUrl='${config.baseUrl}'")
            return false
        }

        val imageUris = observation.allImageUris()
        val identity = currentIdentityProvider() ?: fallbackIdentity()
        if (identity == null) {
            Log.w(TAG, "Skipping sync because there is no active session identity.")
            return false
        }
        if (identity.userId == null && identity.guestLabel.isNullOrBlank()) {
            Log.w(TAG, "Skipping sync because the active session has no remote identity configured.")
            return false
        }

        val payload = RemoteObservationPayload(
            deviceObservationId = observation.id,
            userId = identity.userId,
            guestLabel = identity.guestLabel,
            imageUri = imageUris.firstOrNull() ?: observation.imageUri,
            imageUris = imageUris,
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

        val multipartBody = buildMultipartBody(observation, payload)
            ?: return false

        val url = buildObservationUrl()
        Log.d(
            TAG,
            "Uploading observation with ${imageUris.size} image(s) to $url deviceObservationId=${observation.id} lat=${observation.latitude} lon=${observation.longitude}"
        )
        val request = Request.Builder()
            .url(url)
            .post(multipartBody)
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

    private fun buildMultipartBody(
        observation: ObservationEntity,
        payload: RemoteObservationPayload
    ): MultipartBody? {
        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)

        val metadataBody = gson.toJson(payload)
            .toRequestBody(JSON_MEDIA_TYPE)
        multipartBuilder.addFormDataPart("metadata", null, metadataBody)

        val imageParts = observation.allImageUris().mapIndexedNotNull { index, imageUriString ->
            buildImagePart(observation.id, index, imageUriString)
        }

        if (imageParts.isEmpty()) {
            Log.w(TAG, "Observation image bytes are empty for all uris observationId=${observation.id}")
            return null
        }

        imageParts.forEach { imagePart ->
            multipartBuilder.addPart(imagePart)
        }

        return multipartBuilder.build()
    }

    private fun buildImagePart(
        observationId: String,
        index: Int,
        imageUriString: String
    ): MultipartBody.Part? {
        val imageUri = Uri.parse(imageUriString)
        val contentResolver = appContext.contentResolver
        val mimeType = contentResolver.getType(imageUri)?.takeIf { it.isNotBlank() } ?: "image/jpeg"
        val imageBytes = runCatching {
            openImageInputStream(imageUri)?.use { it.readBytes() }
        }.onFailure { error ->
            Log.e(TAG, "Failed to read observation image uri=$imageUriString", error)
        }.getOrNull()

        if (imageBytes == null || imageBytes.isEmpty()) {
            Log.w(TAG, "Observation image bytes are empty uri=$imageUriString")
            return null
        }

        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.takeIf { it.isNotBlank() }
            ?: "jpg"
        val imageBody = imageBytes.toRequestBody(mimeType.toMediaType())

        return MultipartBody.Part.createFormData(
            "images",
            "${observationId}-${index + 1}.$extension",
            imageBody
        )
    }

    private fun buildObservationUrl(): String {
        return config.baseUrl.trimEnd('/') + "/api/observations"
    }

    private fun fallbackIdentity(): RemoteUserIdentity? {
        return when {
            config.defaultUserId > 0 -> RemoteUserIdentity(userId = config.defaultUserId, guestLabel = null)
            config.guestLabel.isNotBlank() -> RemoteUserIdentity(userId = null, guestLabel = config.guestLabel)
            else -> null
        }
    }

    private fun openImageInputStream(uri: Uri) = when (uri.scheme) {
        "file" -> uri.path?.let { FileInputStream(it) }
        else -> appContext.contentResolver.openInputStream(uri)
    }

    companion object {
        private const val TAG = "RemoteObservationSync"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

