package com.example.geodouro_project.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.geodouro_project.ai.MobileNetV3Classifier
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
import com.example.geodouro_project.domain.model.LocalPredictionCandidate
import com.example.geodouro_project.domain.model.ObservationSaveResult
import com.example.geodouro_project.domain.model.ObservationSyncStatus
import java.util.Locale
import java.util.UUID
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class PlantRepository(
    private val appContext: Context,
    private val taxonCacheDao: TaxonCacheDao,
    private val observationDao: ObservationDao,
    private val apiService: INaturalistApiService,
    private val connectivityChecker: ConnectivityChecker,
    private val imageHttpClient: OkHttpClient
) {

    private val classifier by lazy { MobileNetV3Classifier(appContext) }

    suspend fun rerankLowConfidenceInference(localResult: LocalInferenceResult): LocalInferenceResult {
        if (localResult.confidence >= LOW_CONFIDENCE_THRESHOLD) {
            return localResult
        }

        if (!connectivityChecker.hasInternetConnection()) {
            return localResult
        }

        val baseCandidates = localResult.candidatePredictions
            .ifEmpty {
                listOf(
                    LocalPredictionCandidate(
                        species = localResult.predictedSpecies,
                        confidence = localResult.confidence
                    )
                )
            }
            .take(MAX_RERANK_CANDIDATES)

        if (baseCandidates.size <= 1) {
            return localResult
        }

        return withContext(Dispatchers.IO) {
            val capturedBitmap = decodeLocalBitmap(localResult.imageUri)
                ?: return@withContext localResult
            val queryEmbedding = classifier.extractEmbedding(capturedBitmap)
                ?: return@withContext localResult

            val rankedBySimilarity = baseCandidates
                .map { candidate -> rankCandidate(queryEmbedding, candidate) }
                .sortedByDescending { it.fusedScore }

            val bestCandidate = rankedBySimilarity.firstOrNull()
                ?: return@withContext localResult

            val speciesChanged = !bestCandidate.candidate.species.equals(
                localResult.predictedSpecies,
                ignoreCase = true
            )

            if (!speciesChanged || bestCandidate.fusedScore < localResult.confidence + MIN_RERANK_GAIN) {
                return@withContext localResult
            }

            localResult.copy(
                predictedSpecies = bestCandidate.candidate.species,
                confidence = bestCandidate.candidate.confidence,
                candidatePredictions = rankedBySimilarity.map { it.candidate }
            )
        }
    }

    suspend fun enrichSpecies(speciesName: String): EnrichmentResult {
        val normalizedQuery = speciesName
            .replace('_', ' ')
            .trim()
            .lowercase(Locale.ROOT)
        val cached = taxonCacheDao.getBySpeciesQuery(normalizedQuery)

        if (cached != null) {
            val remoteTaxon = fetchTopTaxon(speciesName)
            if (remoteTaxon != null) {
                val updatedCache = remoteTaxon.toCacheEntity(normalizedQuery)
                taxonCacheDao.upsert(updatedCache)
                return EnrichmentResult(updatedCache.toDomain(), EnrichmentOrigin.NETWORK)
            }
            return EnrichmentResult(cached.toDomain(), EnrichmentOrigin.CACHE)
        }

        val remoteTaxon = fetchTopTaxon(speciesName)
        if (remoteTaxon != null) {
            val freshCache = remoteTaxon.toCacheEntity(normalizedQuery)
            taxonCacheDao.upsert(freshCache)
            return EnrichmentResult(freshCache.toDomain(), EnrichmentOrigin.NETWORK)
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
        val apiQuery = speciesName
            .replace('_', ' ')
            .trim()

        if (apiQuery.isBlank()) {
            return null
        }

        return try {
            val primaryResult = apiService.searchTaxa(apiQuery).results.firstOrNull()
            if (primaryResult != null) {
                primaryResult
            } else {
                val genusQuery = apiQuery.substringBefore(' ').trim()
                if (genusQuery.isBlank() || genusQuery.equals(apiQuery, ignoreCase = true)) {
                    null
                } else {
                    apiService.searchTaxa(genusQuery).results.firstOrNull()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun rankCandidate(
        queryEmbedding: FloatArray,
        candidate: LocalPredictionCandidate
    ): RankedCandidate {
        val referencePhotoUrl = fetchTopTaxon(candidate.species)?.defaultPhoto?.mediumUrl
            ?: return RankedCandidate(candidate = candidate, similarity = 0f, fusedScore = candidate.confidence)

        val referenceBitmap = downloadBitmap(referencePhotoUrl)
            ?: return RankedCandidate(candidate = candidate, similarity = 0f, fusedScore = candidate.confidence)

        val referenceEmbedding = classifier.extractEmbedding(referenceBitmap)
            ?: return RankedCandidate(candidate = candidate, similarity = 0f, fusedScore = candidate.confidence)

        val similarity = cosineSimilarity(queryEmbedding, referenceEmbedding)
            .coerceIn(0f, 1f)

        val fusedScore = (MODEL_WEIGHT * candidate.confidence) + (SIMILARITY_WEIGHT * similarity)
        return RankedCandidate(candidate = candidate, similarity = similarity, fusedScore = fusedScore)
    }

    private fun decodeLocalBitmap(imageUri: String): Bitmap? {
        val parsedUri = Uri.parse(imageUri)
        return runCatching {
            appContext.contentResolver.openInputStream(parsedUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        }.getOrNull()
    }

    private fun downloadBitmap(url: String): Bitmap? {
        val request = Request.Builder()
            .url(url)
            .build()

        return runCatching {
            imageHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use null
                }

                val bytes = response.body?.bytes() ?: return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }

    private fun cosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size || vectorA.isEmpty()) {
            return 0f
        }

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        vectorA.indices.forEach { index ->
            val a = vectorA[index].toDouble()
            val b = vectorB[index].toDouble()
            dotProduct += a * b
            normA += a * a
            normB += b * b
        }

        val denominator = sqrt(normA) * sqrt(normB)
        if (denominator == 0.0) {
            return 0f
        }

        return (dotProduct / denominator).toFloat()
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

    private data class RankedCandidate(
        val candidate: LocalPredictionCandidate,
        val similarity: Float,
        val fusedScore: Float
    )

    companion object {
        private const val LOW_CONFIDENCE_THRESHOLD = 0.30f
        private const val MAX_RERANK_CANDIDATES = 5
        private const val MODEL_WEIGHT = 0.60f
        private const val SIMILARITY_WEIGHT = 0.40f
        private const val MIN_RERANK_GAIN = 0.02f
    }
}
