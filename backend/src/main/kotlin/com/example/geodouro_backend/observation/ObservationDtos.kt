package com.example.geodouro_backend.observation

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class UpsertObservationRequest(
    val deviceObservationId: UUID? = null,
    val userId: Int? = null,
    @field:Size(max = 255)
    val guestLabel: String? = null,
    val imageUri: String? = null,
    val imageUris: List<String>? = null,
    val capturedAt: Long? = null,
    @field:NotBlank
    val predictedScientificName: String,
    @field:DecimalMin("0.0")
    @field:DecimalMax("1.0")
    val confidence: Float? = null,
    val enrichedScientificName: String? = null,
    val enrichedCommonName: String? = null,
    val enrichedFamily: String? = null,
    val enrichedWikipediaUrl: String? = null,
    val enrichedPhotoUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val observedAt: Instant? = null,
    val isPublished: Boolean = false,
    val syncStatus: String? = null,
    val lastSyncAttemptAt: Long? = null,
    val notes: String? = null
)

data class UpdateObservationMetadataRequest(
    val scientificName: String?,
    val commonName: String?,
    val family: String?
)

data class ObservationResponse(
    val observationId: Int,
    val deviceObservationId: UUID?,
    val userId: Int,
    val predictedScientificName: String?,
    val confidence: Float?,
    val syncStatus: String,
    val isPublished: Boolean,
    val observedAt: Instant,
    val storedImagePath: String?
)

data class ObservationDetailResponse(
    val observationId: Int,
    val deviceObservationId: UUID,
    val userId: Int,
    val scientificName: String,
    val commonName: String?,
    val family: String?,
    val wikipediaUrl: String?,
    val photoUrl: String?,
    val imagePaths: List<String>,
    val capturedAt: Long?,
    val confidence: Float?,
    val latitude: Double?,
    val longitude: Double?,
    val notes: String?,
    val syncStatus: String,
    val isPublished: Boolean,
    val observedAt: Instant
)
