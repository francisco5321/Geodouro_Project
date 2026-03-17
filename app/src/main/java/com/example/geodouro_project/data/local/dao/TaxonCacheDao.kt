package com.example.geodouro_project.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.geodouro_project.data.local.entity.TaxonCacheEntity

@Dao
interface TaxonCacheDao {

    @Query("SELECT * FROM taxon_cache WHERE speciesQuery = :speciesQuery LIMIT 1")
    suspend fun getBySpeciesQuery(speciesQuery: String): TaxonCacheEntity?

    @Upsert
    suspend fun upsert(entity: TaxonCacheEntity)
}
