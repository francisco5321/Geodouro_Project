package com.example.geodouro_project.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "taxon_cache")
data class TaxonCacheEntity(
    @PrimaryKey
    val speciesQuery: String,
    val taxonId: Long?,
    val scientificName: String,
    val commonName: String?,
    val family: String?,
    val wikipediaUrl: String?,
    val photoUrl: String?,
    val updatedAt: Long
)
