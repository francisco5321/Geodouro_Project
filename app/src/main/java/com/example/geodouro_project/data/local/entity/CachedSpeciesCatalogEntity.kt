package com.example.geodouro_project.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_species_catalog")
data class CachedSpeciesCatalogEntity(
    @PrimaryKey
    val id: String,
    val scientificName: String,
    val commonName: String?,
    val family: String,
    val genus: String,
    val imageCount: Int,
    val thumbnailUri: String?,
    val wikipediaUrl: String?,
    val updatedAtEpochMs: Long,
    val cachedAt: Long
)
