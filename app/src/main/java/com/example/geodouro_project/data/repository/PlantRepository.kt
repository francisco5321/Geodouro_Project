package com.example.geodouro_project.data.repository

import com.example.geodouro_project.core.network.ConnectivityChecker
import com.example.geodouro_project.data.local.dao.ObservationDao
import com.example.geodouro_project.data.local.dao.TaxonCacheDao
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.example.geodouro_project.data.local.entity.TaxonCacheEntity
import com.example.geodouro_project.data.remote.api.INaturalistApiService
import com.example.geodouro_project.data.remote.model.InaturalistTaxonDto
import com.example.geodouro_project.domain.model.EnrichedSpeciesData
import com.example.geodouro_project.domain.model.EnrichmentOrigin
import com.example.geodouro_project.domain.model.EnrichmentResult
import com.example.geodouro_project.domain.model.LocalInferenceResult
import com.example.geodouro_project.domain.model.ObservationSaveResult
import com.example.geodouro_project.domain.model.ObservationSyncStatus
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

class PlantRepository(
    private val taxonCacheDao: TaxonCacheDao,
    private val observationDao: ObservationDao,
    private val apiService: INaturalistApiService,
    private val connectivityChecker: ConnectivityChecker
) {

    suspend fun enrichSpecies(speciesName: String): EnrichmentResult {
        val normalizedQuery = speciesName.trim().lowercase(Locale.ROOT)
        val cached = taxonCacheDao.getBySpeciesQuery(normalizedQuery)

        if (cached != null) {
            if (connectivityChecker.hasInternetConnection()) {
                val remoteTaxon = fetchTopTaxon(speciesName)
                if (remoteTaxon != null) {
                    val updatedCache = remoteTaxon.toCacheEntity(normalizedQuery)
                    taxonCacheDao.upsert(updatedCache)
                    return EnrichmentResult(updatedCache.toDomain(), EnrichmentOrigin.NETWORK)
                }
            }
            return EnrichmentResult(cached.toDomain(), EnrichmentOrigin.CACHE)
        }

        if (connectivityChecker.hasInternetConnection()) {
            val remoteTaxon = fetchTopTaxon(speciesName)
            if (remoteTaxon != null) {
                val freshCache = remoteTaxon.toCacheEntity(normalizedQuery)
                taxonCacheDao.upsert(freshCache)
                return EnrichmentResult(freshCache.toDomain(), EnrichmentOrigin.NETWORK)
            }
        }

        return EnrichmentResult(data = null, origin = EnrichmentOrigin.LOCAL_ONLY)
    }

    suspend fun saveObservation(
        localResult: LocalInferenceResult,
        enrichedData: EnrichedSpeciesData?
    ): ObservationSaveResult {
        val newObservation = ObservationEntity(
            id = UUID.randomUUID().toString(),
            imageUri = localResult.imageUri,
            capturedAt = localResult.capturedAt,
            latitude = localResult.latitude,
            longitude = localResult.longitude,
            predictedSpecies = localResult.predictedSpecies,
            confidence = localResult.confidence,
            enrichedScientificName = enrichedData?.scientificName,
            enrichedCommonName = enrichedData?.commonName,
            enrichedFamily = enrichedData?.family,
            enrichedWikipediaUrl = enrichedData?.wikipediaUrl,
            enrichedPhotoUrl = enrichedData?.photoUrl,
            syncStatus = ObservationSyncStatus.PENDING.name,
            lastSyncAttemptAt = null
        )

        observationDao.upsert(newObservation)

        if (connectivityChecker.hasInternetConnection()) {
            syncPendingObservations()
        }

        val persisted = observationDao.getById(newObservation.id)
        val finalStatus = persisted?.syncStatus
            ?.let { ObservationSyncStatus.valueOf(it) }
            ?: ObservationSyncStatus.PENDING

        return ObservationSaveResult(
            observationId = newObservation.id,
            syncStatus = finalStatus
        )
    }

    suspend fun syncPendingObservations(): Int {
        if (!connectivityChecker.hasInternetConnection()) {
            return 0
        }

        val pendingObservations = observationDao.getBySyncStatus(ObservationSyncStatus.PENDING.name)
        var syncedCount = 0

        pendingObservations.forEach { observation ->
            val success = uploadObservationToRemote(observation)
            val attemptTime = System.currentTimeMillis()

            if (success) {
                observationDao.updateSyncStatus(
                    id = observation.id,
                    syncStatus = ObservationSyncStatus.SYNCED.name,
                    syncTimestamp = attemptTime
                )
                syncedCount++
            } else {
                observationDao.updateSyncStatus(
                    id = observation.id,
                    syncStatus = ObservationSyncStatus.FAILED.name,
                    syncTimestamp = attemptTime
                )
            }
        }

        return syncedCount
    }

    fun observeObservations(): Flow<List<ObservationEntity>> {
        return observationDao.observeAll()
    }

    private suspend fun fetchTopTaxon(speciesName: String): InaturalistTaxonDto? {
        return try {
            apiService.searchTaxa(speciesName).results.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun uploadObservationToRemote(observation: ObservationEntity): Boolean {
        // Placeholder de sincronizacao remota: integrar endpoint real quando disponivel.
        delay(150)
        return observation.predictedSpecies.isNotBlank()
    }

    private fun InaturalistTaxonDto.toCacheEntity(query: String): TaxonCacheEntity {
        return TaxonCacheEntity(
            speciesQuery = query,
            taxonId = id,
            scientificName = scientificName ?: query,
            commonName = commonName,
            family = iconicTaxonName,
            wikipediaUrl = wikipediaUrl,
            photoUrl = defaultPhoto?.mediumUrl,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun TaxonCacheEntity.toDomain(): EnrichedSpeciesData {
        return EnrichedSpeciesData(
            taxonId = taxonId,
            queriedSpecies = speciesQuery,
            scientificName = scientificName,
            commonName = commonName,
            family = family,
            wikipediaUrl = wikipediaUrl,
            photoUrl = photoUrl,
            updatedAt = updatedAt
        )
    }
}
