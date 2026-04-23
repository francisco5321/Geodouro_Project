package com.example.geodouro_backend.species

import java.time.Instant
import java.util.UUID

data class PlantSpeciesResponse(
    val plantSpeciesId: Int,
    val speciesId: String,
    val scientificName: String,
    val commonName: String?,
    val family: String,
    val genus: String,
    val species: String,
    val imageCount: Int,
    val thumbnailPath: String?,
    val wikipediaUrl: String?,
    val updatedAt: Instant
)

data class PlantSpeciesDetailResponse(
    val plantSpeciesId: Int,
    val speciesId: String,
    val scientificName: String,
    val commonName: String?,
    val family: String,
    val genus: String,
    val species: String,
    val imageCount: Int,
    val observationCount: Int,
    val syncedCount: Int,
    val publishedCount: Int,
    val wikipediaUrl: String?,
    val heroImagePath: String?,
    val galleryImagePaths: List<String>,
    val locationSummary: String,
    val observations: List<PlantSpeciesObservationResponse>
)

data class PlantSpeciesObservationResponse(
    val deviceObservationId: UUID,
    val scientificName: String,
    val commonName: String?,
    val userDisplayName: String?,
    val capturedAt: Long?,
    val confidence: Float?,
    val syncStatus: String,
    val isPublished: Boolean,
    val imagePath: String?
)
