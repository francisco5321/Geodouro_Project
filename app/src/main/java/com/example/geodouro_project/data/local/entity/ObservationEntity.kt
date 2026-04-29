package com.example.geodouro_project.data.local.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "observation",
    indices = [
        Index(value = ["syncStatus"]),
        Index(value = ["capturedAt"]),
        Index(value = ["ownerUserId"]),
        Index(value = ["ownerGuestLabel"])
    ]
)
data class ObservationEntity(
    @PrimaryKey
    val id: String,
    val ownerUserId: Int?,
    val ownerGuestLabel: String?,
    val imageUri: String,
    val imageUrisSerialized: String,
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
    val requiresManualIdentification: Boolean,
    val notes: String?,
    val isPublished: Boolean,
    val syncStatus: String,
    val lastSyncAttemptAt: Long?
) {
    @Ignore
    var publishedByDisplayName: String? = null

    fun allImageUris(): List<String> {
        val parsed = imageUrisSerialized
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return if (parsed.isNotEmpty()) {
            parsed
        } else {
            listOfNotNull(imageUri.takeIf { it.isNotBlank() })
        }
    }

    companion object {
        fun serializeImageUris(imageUris: List<String>, fallbackImageUri: String): String {
            val normalized = imageUris
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            return if (normalized.isNotEmpty()) {
                normalized.joinToString(separator = "\n")
            } else {
                fallbackImageUri.trim()
            }
        }
    }
}
