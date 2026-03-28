package com.example.geodouro_project.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "observation",
    indices = [Index(value = ["syncStatus"]), Index(value = ["capturedAt"])]
)
data class ObservationEntity(
    @PrimaryKey
    val id: String,
    val imageUri: String,
    val capturedAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val predictedSpecies: String,
    val confidence: Float,
    val enrichedScientificName: String?,
    val enrichedCommonName: String?,
    val enrichedFamily: String?,
    val enrichedWikipediaUrl: String?,
    val enrichedPhotoUrl: String?,
    val syncStatus: String,
    val lastSyncAttemptAt: Long?
)
