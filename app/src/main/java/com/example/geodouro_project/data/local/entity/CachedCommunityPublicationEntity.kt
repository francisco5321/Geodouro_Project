package com.example.geodouro_project.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_community_publication")
data class CachedCommunityPublicationEntity(
    @PrimaryKey
    val id: String,
    val publicationId: Int,
    val scientificName: String,
    val commonName: String?,
    val userDisplayName: String,
    val latitude: Double?,
    val longitude: Double?,
    val publishedAt: String,
    val imageUrl: String?,
    val cachedAt: Long
)
