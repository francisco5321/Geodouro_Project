package com.example.geodouro_project.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.example.geodouro_project.data.remote.model.RemoteObservationPayload
import com.example.geodouro_project.domain.model.ObservationSyncStatus
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
        if (!identityMatchesObservationOwner(identity, observation)) {
            Log.w(TAG, "Skipping sync because observation ${observation.id} does not belong to the active session.")
            return false
        }
        if (identity.authToken.isNullOrBlank() && identity.userId == null && identity.guestLabel.isNullOrBlank()) {
            Log.w(TAG, "Skipping sync because the active session has no remote identity configured.")
            return false
        }

        val payload = RemoteObservationPayload(
            deviceObservationId = observation.id,
            userId = identity.userId,
            guestLabel = identity.guestLabel,
            requiresManualIdentification = observation.requiresManualIdentification,
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
            lastSyncAttemptAt = syncAttemptAt,
            notes = observation.notes
        )

        val multipartBody = buildMultipartBody(observation, payload)
            ?: return false

        val url = buildObservationUrl()
        Log.d(
            TAG,
            "Uploading observation with ${imageUris.size} image(s) to $url deviceObservationId=${observation.id} lat=${observation.latitude} lon=${observation.longitude}"
        )
        val requestBuilder = Request.Builder()
            .url(url)
            .post(multipartBody)

        identity.authToken?.takeIf { it.isNotBlank() }?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()

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
        val imageBytes = runCatching {
            prepareUploadImage(imageUri)
        }.onFailure { error ->
            Log.e(TAG, "Failed to read observation image uri=$imageUriString", error)
        }.getOrNull()

        if (imageBytes == null || imageBytes.bytes.isEmpty()) {
            Log.w(TAG, "Observation image bytes are empty uri=$imageUriString")
            return null
        }

        val imageBody = imageBytes.bytes.toRequestBody(imageBytes.mimeType.toMediaType())

        return MultipartBody.Part.createFormData(
            "images",
            "${observationId}-${index + 1}.${imageBytes.extension}",
            imageBody
        )
    }

    private fun prepareUploadImage(imageUri: Uri): UploadImagePayload? {
        val originalMimeType = appContext.contentResolver.getType(imageUri)
            ?.takeIf { it.isNotBlank() }
            ?: "image/jpeg"

        val originalBytes = openImageInputStream(imageUri)?.use { it.readBytes() }
            ?: return null

        if (originalBytes.size <= MAX_IMAGE_UPLOAD_BYTES) {
            val originalExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(originalMimeType)
                ?.takeIf { it.isNotBlank() }
                ?: "jpg"
            return UploadImagePayload(originalBytes, originalMimeType, originalExtension)
        }

        val sampledBitmap = decodeSampledBitmap(imageUri)
        if (sampledBitmap == null) {
            Log.w(TAG, "Falling back to original image bytes after failed bitmap decode uri=$imageUri")
            val originalExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(originalMimeType)
                ?.takeIf { it.isNotBlank() }
                ?: "jpg"
            return UploadImagePayload(originalBytes, originalMimeType, originalExtension)
        }

        return try {
            val compressedBytes = compressBitmap(sampledBitmap)
            Log.d(
                TAG,
                "Compressed upload image uri=$imageUri originalBytes=${originalBytes.size} compressedBytes=${compressedBytes.size}"
            )
            UploadImagePayload(compressedBytes, COMPRESSED_IMAGE_MIME_TYPE, COMPRESSED_IMAGE_EXTENSION)
        } finally {
            sampledBitmap.recycle()
        }
    }

    private fun decodeSampledBitmap(imageUri: Uri): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        openImageInputStream(imageUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        } ?: return null

        val sampleSize = calculateInSampleSize(boundsOptions, MAX_IMAGE_DIMENSION_PX, MAX_IMAGE_DIMENSION_PX)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }

        return openImageInputStream(imageUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height, width) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize.coerceAtLeast(1)
    }

    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        var quality = INITIAL_JPEG_QUALITY
        var compressedBytes = bitmap.toJpegByteArray(quality)

        while (compressedBytes.size > TARGET_COMPRESSED_IMAGE_BYTES && quality > MIN_JPEG_QUALITY) {
            quality -= JPEG_QUALITY_STEP
            compressedBytes = bitmap.toJpegByteArray(quality)
        }

        return compressedBytes
    }

    private fun Bitmap.toJpegByteArray(quality: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }

    private fun buildObservationUrl(): String {
        return config.baseUrl.trimEnd('/') + "/api/observations"
    }

    private fun fallbackIdentity(): RemoteUserIdentity? {
        return when {
            config.defaultUserId > 0 -> RemoteUserIdentity(userId = config.defaultUserId, guestLabel = null, authToken = null)
            config.guestLabel.isNotBlank() -> RemoteUserIdentity(userId = null, guestLabel = config.guestLabel, authToken = null)
            else -> null
        }
    }

    private fun identityMatchesObservationOwner(
        identity: RemoteUserIdentity,
        observation: ObservationEntity
    ): Boolean {
        return when {
            observation.ownerUserId != null -> observation.ownerUserId == identity.userId
            !observation.ownerGuestLabel.isNullOrBlank() -> observation.ownerGuestLabel == identity.guestLabel
            else -> false
        }
    }

    private fun openImageInputStream(uri: Uri) = when (uri.scheme) {
        "file" -> uri.path?.let { FileInputStream(it) }
        else -> appContext.contentResolver.openInputStream(uri)
    }

    companion object {
        private const val TAG = "RemoteObservationSync"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val MAX_IMAGE_UPLOAD_BYTES = 4 * 1024 * 1024
        private const val TARGET_COMPRESSED_IMAGE_BYTES = 3 * 1024 * 1024
        private const val MAX_IMAGE_DIMENSION_PX = 1600
        private const val INITIAL_JPEG_QUALITY = 85
        private const val MIN_JPEG_QUALITY = 45
        private const val JPEG_QUALITY_STEP = 10
        private const val COMPRESSED_IMAGE_MIME_TYPE = "image/jpeg"
        private const val COMPRESSED_IMAGE_EXTENSION = "jpg"
    }
}

private data class UploadImagePayload(
    val bytes: ByteArray,
    val mimeType: String,
    val extension: String
)
