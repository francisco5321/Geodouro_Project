package com.example.geodouro_backend.publication

import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class PublishObservationRequest(
    @field:NotNull
    val deviceObservationId: UUID,
    val title: String? = null,
    val description: String? = null
)

data class SavePublicationRequest(
    val title: String? = null,
    val description: String? = null,
    val status: String? = null
)

data class PublicationResponse(
    val publicationId: Int,
    val observationId: Int,
    val deviceObservationId: UUID,
    val userId: Int,
    val plantSpeciesId: Int?,
    val userDisplayName: String,
    val title: String?,
    val description: String?,
    val scientificName: String,
    val commonName: String?,
    val imagePath: String?,
    val latitude: Double?,
    val longitude: Double?,
    val publishedAt: Instant
)
