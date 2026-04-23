package com.example.geodouro_project.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class RemoteSpeciesService(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val config: RemoteDbConfig
) {

    fun isConfigured(): Boolean = config.isConfigured()

    fun fetchSpecies(): List<RemotePlantSpecies> {
        if (!isConfigured()) {
            return emptyList()
        }

        val request = Request.Builder()
            .url(
                config.baseUrl.trimEnd('/')
                    .toHttpUrl()
                    .newBuilder()
                    .addPathSegment("api")
                    .addPathSegment("species")
                    .build()
            )
            .get()
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Fetch species failed code=${response.code}")
                    return@use emptyList()
                }

                val body = response.body?.string().orEmpty()
                gson.fromJson(body, Array<RemotePlantSpeciesResponse>::class.java)
                    ?.map { species -> species.toDomain() }
                    .orEmpty()
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to fetch species", error)
        }.getOrDefault(emptyList())
    }

    fun fetchSpeciesDetail(speciesId: String): RemotePlantSpeciesDetail? {
        if (!isConfigured()) {
            return null
        }

        val request = Request.Builder()
            .url(
                config.baseUrl.trimEnd('/')
                    .toHttpUrl()
                    .newBuilder()
                    .addPathSegment("api")
                    .addPathSegment("species")
                    .addPathSegment(speciesId.trim())
                    .build()
            )
            .get()
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Fetch species detail failed code=${response.code} speciesId=$speciesId")
                    return@use null
                }

                val body = response.body?.string().orEmpty()
                gson.fromJson(body, RemotePlantSpeciesDetailResponse::class.java)?.toDomain()
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to fetch species detail $speciesId", error)
        }.getOrNull()
    }

    private fun RemotePlantSpeciesResponse.toDomain(): RemotePlantSpecies {
        return RemotePlantSpecies(
            id = speciesId,
            scientificName = scientificName,
            commonName = commonName,
            family = family,
            genus = genus,
            imageCount = imageCount,
            thumbnailUrl = thumbnailPath?.let(::resolveImageUrl),
            wikipediaUrl = wikipediaUrl,
            updatedAtEpochMs = parseInstantToEpochMillis(updatedAt)
        )
    }

    private fun RemotePlantSpeciesDetailResponse.toDomain(): RemotePlantSpeciesDetail {
        return RemotePlantSpeciesDetail(
            id = speciesId,
            scientificName = scientificName,
            commonName = commonName,
            family = family,
            genus = genus,
            imageCount = imageCount,
            observationCount = observationCount,
            syncedCount = syncedCount,
            publishedCount = publishedCount,
            wikipediaUrl = wikipediaUrl,
            heroImageUrl = heroImagePath?.let(::resolveImageUrl),
            galleryImageUrls = galleryImagePaths.map(::resolveImageUrl),
            locationSummary = locationSummary,
            observations = observations.map { observation ->
                RemoteSpeciesObservation(
                    deviceObservationId = observation.deviceObservationId,
                    scientificName = observation.scientificName,
                    commonName = observation.commonName,
                    userDisplayName = observation.userDisplayName,
                    capturedAt = observation.capturedAt ?: 0L,
                    confidence = observation.confidence ?: 0f,
                    syncStatus = observation.syncStatus,
                    isPublished = observation.isPublished,
                    imageUrl = observation.imagePath?.let(::resolveImageUrl)
                )
            }
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

    private fun parseInstantToEpochMillis(value: String?): Long {
        return runCatching { Instant.parse(value).toEpochMilli() }
            .getOrDefault(0L)
    }

    companion object {
        private const val TAG = "RemoteSpecies"
    }
}

data class RemotePlantSpecies(
    val id: String,
    val scientificName: String,
    val commonName: String?,
    val family: String,
    val genus: String,
    val imageCount: Int,
    val thumbnailUrl: String?,
    val wikipediaUrl: String?,
    val updatedAtEpochMs: Long
)

data class RemotePlantSpeciesDetail(
    val id: String,
    val scientificName: String,
    val commonName: String?,
    val family: String,
    val genus: String,
    val imageCount: Int,
    val observationCount: Int,
    val syncedCount: Int,
    val publishedCount: Int,
    val wikipediaUrl: String?,
    val heroImageUrl: String?,
    val galleryImageUrls: List<String>,
    val locationSummary: String,
    val observations: List<RemoteSpeciesObservation>
)

data class RemoteSpeciesObservation(
    val deviceObservationId: String,
    val scientificName: String,
    val commonName: String?,
    val userDisplayName: String?,
    val capturedAt: Long,
    val confidence: Float,
    val syncStatus: String,
    val isPublished: Boolean,
    val imageUrl: String?
)

private data class RemotePlantSpeciesResponse(
    @SerializedName("speciesId")
    val speciesId: String,
    @SerializedName("scientificName")
    val scientificName: String,
    @SerializedName("commonName")
    val commonName: String?,
    @SerializedName("family")
    val family: String,
    @SerializedName("genus")
    val genus: String,
    @SerializedName("imageCount")
    val imageCount: Int,
    @SerializedName("thumbnailPath")
    val thumbnailPath: String?,
    @SerializedName("wikipediaUrl")
    val wikipediaUrl: String?,
    @SerializedName("updatedAt")
    val updatedAt: String?
)

private data class RemotePlantSpeciesDetailResponse(
    @SerializedName("speciesId")
    val speciesId: String,
    @SerializedName("scientificName")
    val scientificName: String,
    @SerializedName("commonName")
    val commonName: String?,
    @SerializedName("family")
    val family: String,
    @SerializedName("genus")
    val genus: String,
    @SerializedName("imageCount")
    val imageCount: Int,
    @SerializedName("observationCount")
    val observationCount: Int,
    @SerializedName("syncedCount")
    val syncedCount: Int,
    @SerializedName("publishedCount")
    val publishedCount: Int,
    @SerializedName("wikipediaUrl")
    val wikipediaUrl: String?,
    @SerializedName("heroImagePath")
    val heroImagePath: String?,
    @SerializedName("galleryImagePaths")
    val galleryImagePaths: List<String>,
    @SerializedName("locationSummary")
    val locationSummary: String,
    @SerializedName("observations")
    val observations: List<RemoteSpeciesObservationResponse>
)

private data class RemoteSpeciesObservationResponse(
    @SerializedName("deviceObservationId")
    val deviceObservationId: String,
    @SerializedName("scientificName")
    val scientificName: String,
    @SerializedName("commonName")
    val commonName: String?,
    @SerializedName("userDisplayName")
    val userDisplayName: String?,
    @SerializedName("capturedAt")
    val capturedAt: Long?,
    @SerializedName("confidence")
    val confidence: Float?,
    @SerializedName("syncStatus")
    val syncStatus: String,
    @SerializedName("isPublished")
    val isPublished: Boolean,
    @SerializedName("imagePath")
    val imagePath: String?
)
