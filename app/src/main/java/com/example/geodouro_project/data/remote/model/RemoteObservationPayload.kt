package com.example.geodouro_project.data.remote.model

data class RemoteObservationPayload(
    val deviceObservationId: String,
    val userId: Int?,
    val guestLabel: String?,
    val imageUri: String,
    val capturedAt: Long,
    val predictedScientificName: String,
    val confidence: Float,
    val enrichedScientificName: String?,
    val enrichedCommonName: String?,
    val enrichedFamily: String?,
    val enrichedWikipediaUrl: String?,
    val enrichedPhotoUrl: String?,
    val latitude: Double?,
    val longitude: Double?,
    val syncStatus: String,
    val lastSyncAttemptAt: Long
)
