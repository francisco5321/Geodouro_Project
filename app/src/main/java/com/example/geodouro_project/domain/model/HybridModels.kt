package com.example.geodouro_project.domain.model

enum class EnrichmentOrigin {
    CACHE,
    NETWORK,
    LOCAL_ONLY
}

enum class ObservationSyncStatus {
    PENDING,
    SYNCED,
    FAILED
}

data class EnrichedSpeciesData(
    val taxonId: Long?,
    val queriedSpecies: String,
    val scientificName: String,
    val commonName: String?,
    val family: String?,
    val wikipediaUrl: String?,
    val photoUrl: String?,
    val updatedAt: Long
)

data class LocalPredictionCandidate(
    val species: String,
    val confidence: Float
)

data class LocalInferenceResult(
    val imageUri: String,
    val capturedAt: Long = System.currentTimeMillis(),
    val latitude: Double?,
    val longitude: Double?,
    val predictedSpecies: String,
    val confidence: Float,
    val candidatePredictions: List<LocalPredictionCandidate> = emptyList(),
    val rejectionReason: String? = null
)

data class EnrichmentResult(
    val data: EnrichedSpeciesData?,
    val origin: EnrichmentOrigin
)

data class ObservationSaveResult(
    val observationId: String,
    val syncStatus: ObservationSyncStatus
)
