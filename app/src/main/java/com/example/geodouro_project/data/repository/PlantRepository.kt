package com.example.geodouro_project.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.geodouro_project.ai.MobileNetV3Classifier
import com.example.geodouro_project.core.network.ConnectivityChecker
import com.example.geodouro_project.data.local.dao.ObservationDao
import com.example.geodouro_project.data.local.dao.TaxonCacheDao
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.example.geodouro_project.data.local.entity.TaxonCacheEntity
import com.example.geodouro_project.data.remote.RemoteObservationSyncService
import com.example.geodouro_project.data.remote.RemoteCommunityPublication
import com.example.geodouro_project.data.remote.RemotePublicationService
import com.example.geodouro_project.data.remote.api.INaturalistApiService
import com.example.geodouro_project.data.remote.model.InaturalistTaxonDto
import com.example.geodouro_project.domain.model.ConfidencePolicy
import com.example.geodouro_project.domain.model.ConfidenceState
import com.example.geodouro_project.domain.model.EnrichedSpeciesData
import com.example.geodouro_project.domain.model.EnrichmentOrigin
import com.example.geodouro_project.domain.model.EnrichmentResult
import com.example.geodouro_project.domain.model.ImageInferenceResult
import com.example.geodouro_project.domain.model.LocalInferenceResult
import com.example.geodouro_project.domain.model.LocalPredictionCandidate
import com.example.geodouro_project.domain.model.MultiImageAggregationConfig
import com.example.geodouro_project.domain.model.MultiImageAggregationResult
import com.example.geodouro_project.domain.model.ObservationSaveResult
import com.example.geodouro_project.domain.model.ObservationSyncStatus
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import java.util.UUID
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class PlantRepository(
    private val appContext: Context,
    private val taxonCacheDao: TaxonCacheDao,
    private val observationDao: ObservationDao,
    private val apiService: INaturalistApiService,
    private val connectivityChecker: ConnectivityChecker,
    private val imageHttpClient: OkHttpClient,
    private val remoteObservationSyncService: RemoteObservationSyncService,
    private val remotePublicationService: RemotePublicationService
) {

    private val classifier by lazy { MobileNetV3Classifier(appContext) }
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(appContext) }

    suspend fun rerankLowConfidenceInference(localResult: LocalInferenceResult): LocalInferenceResult {
        if (isNonPlantPrediction(localResult.predictedSpecies) || localResult.confidence >= LOW_CONFIDENCE_THRESHOLD) {
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
            .filterNot { isNonPlantPrediction(it.species) }

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
        if (isNonPlantPrediction(speciesName)) {
            return EnrichmentResult(data = null, origin = EnrichmentOrigin.LOCAL_ONLY)
        }

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
        enrichedData: EnrichedSpeciesData?,
        imageUris: List<String> = listOf(localResult.imageUri)
    ): ObservationSaveResult {
        require(!isNonPlantPrediction(localResult.predictedSpecies)) {
            "Nao e possivel guardar uma observacao sem planta reconhecida"
        }

        val resolvedLocation = resolveBestEffortLocation(localResult.latitude, localResult.longitude)
        val normalizedImageUris = imageUris
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf(localResult.imageUri) }

        val primaryImageUri = normalizedImageUris.firstOrNull().orEmpty()
        val newObservation = ObservationEntity(
            id = UUID.randomUUID().toString(),
            imageUri = primaryImageUri,
            imageUrisSerialized = ObservationEntity.serializeImageUris(normalizedImageUris, primaryImageUri),
            capturedAt = localResult.capturedAt,
            latitude = resolvedLocation?.first,
            longitude = resolvedLocation?.second,
            predictedSpecies = localResult.predictedSpecies,
            confidence = localResult.confidence,
            enrichedScientificName = enrichedData?.scientificName,
            enrichedCommonName = enrichedData?.commonName,
            enrichedFamily = enrichedData?.family,
            enrichedWikipediaUrl = enrichedData?.wikipediaUrl,
            enrichedPhotoUrl = enrichedData?.photoUrl,
            isPublished = false,
            syncStatus = ObservationSyncStatus.PENDING.name,
            lastSyncAttemptAt = null
        )

        Log.d(
            TAG,
            "Persisting observation locally id=${newObservation.id} species=${newObservation.predictedSpecies} imageCount=${normalizedImageUris.size} lat=${newObservation.latitude} lon=${newObservation.longitude}"
        )
        observationDao.upsert(newObservation)

        val hasInternet = connectivityChecker.hasInternetConnection()
        val isConfigured = remoteObservationSyncService.isConfigured()
        Log.d(TAG, "saveObservation connectivity=$hasInternet backendConfigured=$isConfigured")
        if (hasInternet && isConfigured) {
            syncPendingObservations()
        }

        val persisted = observationDao.getById(newObservation.id)
        val finalStatus = persisted?.syncStatus
            ?.let { ObservationSyncStatus.valueOf(it) }
            ?: ObservationSyncStatus.PENDING

        Log.d(TAG, "Local observation persisted id=${newObservation.id} finalStatus=$finalStatus")
        return ObservationSaveResult(
            observationId = newObservation.id,
            syncStatus = finalStatus
        )
    }

    suspend fun syncPendingObservations(): Int {
        val hasInternet = connectivityChecker.hasInternetConnection()
        val isConfigured = remoteObservationSyncService.isConfigured()
        Log.d(TAG, "syncPendingObservations connectivity=$hasInternet backendConfigured=$isConfigured")
        if (!hasInternet || !isConfigured) {
            return 0
        }

        val pendingObservations = observationDao.getBySyncStatuses(
            listOf(
                ObservationSyncStatus.PENDING.name,
                ObservationSyncStatus.FAILED.name
            )
        )
        Log.d(TAG, "syncPendingObservations found ${pendingObservations.size} pending/failed observations")
        var syncedCount = 0

        pendingObservations.forEach { observation ->
            val attemptTime = System.currentTimeMillis()
            Log.d(
                TAG,
                "Uploading observation id=${observation.id} species=${observation.predictedSpecies} imageCount=${observation.allImageUris().size} lat=${observation.latitude} lon=${observation.longitude}"
            )
            val success = uploadObservationToRemote(observation, attemptTime)

            if (success) {
                observationDao.updateSyncStatus(
                    id = observation.id,
                    syncStatus = ObservationSyncStatus.SYNCED.name,
                    syncTimestamp = attemptTime
                )
                syncedCount++
                Log.d(TAG, "Observation synced successfully id=${observation.id}")
            } else {
                observationDao.updateSyncStatus(
                    id = observation.id,
                    syncStatus = ObservationSyncStatus.FAILED.name,
                    syncTimestamp = attemptTime
                )
                Log.w(TAG, "Observation sync failed id=${observation.id}")
            }
        }

        return syncedCount
    }

    fun observeObservations(): Flow<List<ObservationEntity>> {
        return observationDao.observeAll()
    }

    fun observePublishedObservations(): Flow<List<ObservationEntity>> {
        return observationDao.observePublished()
    }

    suspend fun publishObservation(observationId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val observation = observationDao.getById(observationId) ?: return@withContext false
            if (observation.syncStatus != ObservationSyncStatus.SYNCED.name) {
                return@withContext false
            }

            if (!connectivityChecker.hasInternetConnection() || !remotePublicationService.isConfigured()) {
                return@withContext false
            }

            val published = remotePublicationService.publishObservation(observation)
            if (published) {
                observationDao.updatePublicationStatus(observationId, true)
            }
            published
        }
    }

    suspend fun updateObservationMetadata(
        observationId: String,
        scientificName: String,
        commonName: String,
        family: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val observation = observationDao.getById(observationId) ?: return@withContext false
            if (observation.isPublished) {
                return@withContext false
            }

            val normalizedScientificName = scientificName.trim().ifBlank { null }
            val normalizedCommonName = commonName.trim().ifBlank { null }
            val normalizedFamily = family.trim().ifBlank { null }

            observationDao.updateObservationMetadata(
                id = observationId,
                scientificName = normalizedScientificName,
                commonName = normalizedCommonName,
                family = normalizedFamily
            )
            true
        }
    }

    fun observeObservationStats(): Flow<ObservationStats> {
        return observationDao.observeAll().map { observations ->
            ObservationStats(
                observationsCount = observations.size,
                publishedCount = observations.count { it.isPublished },
                speciesCount = observations
                    .map { it.enrichedScientificName ?: it.predictedSpecies }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .size
            )
        }
    }

    suspend fun fetchCommunityPublications(): List<CommunityPublication> {
        return withContext(Dispatchers.IO) {
            val remotePublications = if (
                connectivityChecker.hasInternetConnection() && remotePublicationService.isConfigured()
            ) {
                remotePublicationService.fetchPublications()
            } else {
                emptyList()
            }

            if (remotePublications.isNotEmpty()) {
                return@withContext remotePublications.map { it.toDomain() }
            }

            observationDao.getPublished().map { observation ->
                CommunityPublication(
                    id = observation.id,
                    scientificName = observation.enrichedScientificName ?: observation.predictedSpecies,
                    commonName = observation.enrichedCommonName,
                    userDisplayName = "Utilizador",
                    latitude = observation.latitude,
                    longitude = observation.longitude,
                    publishedAt = Instant.ofEpochMilli(observation.capturedAt).toString(),
                    imageUrl = observation.imageUri
                )
            }
        }
    }

    private suspend fun resolveBestEffortLocation(
        fallbackLatitude: Double?,
        fallbackLongitude: Double?
    ): Pair<Double, Double>? = withContext(Dispatchers.Main) {
        if (fallbackLatitude != null && fallbackLongitude != null) {
            return@withContext fallbackLatitude to fallbackLongitude
        }

        if (!hasLocationPermission()) {
            return@withContext null
        }

        fetchCurrentLocationCoordinates() ?: fallbackLatitude?.let { latitude ->
            fallbackLongitude?.let { longitude -> latitude to longitude }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun fetchCurrentLocationCoordinates(): Pair<Double, Double>? {
        val fusedCoordinates = fetchFusedLocationCoordinates()
        if (fusedCoordinates != null) {
            return fusedCoordinates
        }

        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        } ?: return lastKnownLocationCoordinates(locationManager)

        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(appContext)
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }

            runCatching {
                locationManager.getCurrentLocation(provider, cancellationSignal, executor) { location ->
                    if (continuation.isActive) {
                        continuation.resume(location?.let { it.latitude to it.longitude })
                    }
                }
            }.onFailure {
                if (continuation.isActive) {
                    continuation.resume(lastKnownLocationCoordinates(locationManager))
                }
            }
        }
    }

    private suspend fun fetchFusedLocationCoordinates(): Pair<Double, Double>? {
        return suspendCancellableCoroutine { continuation ->
            runCatching {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        if (continuation.isActive) {
                            continuation.resume(location?.let { it.latitude to it.longitude })
                        }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
            }.onFailure {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        } ?: suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (continuation.isActive) {
                        continuation.resume(location?.let { it.latitude to it.longitude })
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        }
    }

    private fun lastKnownLocationCoordinates(locationManager: LocationManager): Pair<Double, Double>? {
        return sequenceOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
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

    private suspend fun uploadObservationToRemote(
        observation: ObservationEntity,
        syncAttemptAt: Long
    ): Boolean = withContext(Dispatchers.IO) {
        remoteObservationSyncService.uploadObservation(
            observation = observation,
            syncAttemptAt = syncAttemptAt
        )
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

    private fun isNonPlantPrediction(species: String): Boolean {
        return species.trim().equals(MobileNetV3Classifier.NON_PLANT_LABEL, ignoreCase = true)
    }

    private data class RankedCandidate(
        val candidate: LocalPredictionCandidate,
        val similarity: Float,
        val fusedScore: Float
    )

    data class ObservationStats(
        val observationsCount: Int,
        val publishedCount: Int,
        val speciesCount: Int
    )

    data class CommunityPublication(
        val id: String,
        val scientificName: String,
        val commonName: String?,
        val userDisplayName: String,
        val latitude: Double?,
        val longitude: Double?,
        val publishedAt: String,
        val imageUrl: String?
    )

    private fun RemoteCommunityPublication.toDomain(): CommunityPublication {
        return CommunityPublication(
            id = publicationId.toString(),
            scientificName = scientificName,
            commonName = commonName,
            userDisplayName = userDisplayName,
            latitude = latitude,
            longitude = longitude,
            publishedAt = publishedAt,
            imageUrl = imageUrl
        )
    }

    suspend fun inferMultipleImages(
        imageUris: List<String>,
        config: MultiImageAggregationConfig = MultiImageAggregationConfig()
    ): MultiImageAggregationResult = withContext(Dispatchers.Default) {
        if (imageUris.isEmpty() || imageUris.size < config.minImagesRequired) {
            throw IllegalArgumentException(
                "Numero de imagens invalido: ${imageUris.size}. " +
                    "Requerido: ${config.minImagesRequired}-${config.maxImagesRequired}"
            )
        }

        val imagesToProcess = imageUris.take(config.maxImagesRequired)
        val startTime = System.currentTimeMillis()

        val imageResults = imagesToProcess.map { imageUri ->
            runCatching {
                val bitmap = decodeLocalBitmap(imageUri)
                    ?: return@runCatching ImageInferenceResult(
                        imageUri = imageUri,
                        predictedSpecies = "UNKNOWN",
                        confidence = 0f,
                        candidatePredictions = emptyList()
                    )

                val prediction = classifier.classify(bitmap)
                val embedding = classifier.extractEmbedding(bitmap)

                ImageInferenceResult(
                    imageUri = imageUri,
                    predictedSpecies = prediction.label,
                    confidence = prediction.confidence,
                    candidatePredictions = prediction.candidates.map { candidate ->
                        LocalPredictionCandidate(
                            species = candidate.label,
                            confidence = candidate.confidence
                        )
                    },
                    embedding = embedding,
                    capturedAt = System.currentTimeMillis()
                )
            }.getOrNull() ?: ImageInferenceResult(
                imageUri = imageUri,
                predictedSpecies = "ERROR",
                confidence = 0f,
                candidatePredictions = emptyList()
            )
        }

        val processingTimeMs = System.currentTimeMillis() - startTime

        return@withContext aggregateMultipleInferences(
            imageResults = imageResults,
            config = config,
            processingTimeMs = processingTimeMs
        )
    }

    suspend fun aggregateMultipleInferences(
        imageResults: List<ImageInferenceResult>,
        config: MultiImageAggregationConfig = MultiImageAggregationConfig(),
        processingTimeMs: Long = 0
    ): MultiImageAggregationResult = withContext(Dispatchers.Default) {
        if (imageResults.isEmpty()) {
            throw IllegalArgumentException("Lista de imagens nao pode estar vazia")
        }

        val successfulResults = imageResults.filter { it.predictedSpecies != "ERROR" && it.predictedSpecies != "UNKNOWN" }
        val validResults = successfulResults.filterNot { isNonPlantPrediction(it.predictedSpecies) }
        if (successfulResults.isEmpty()) {
            throw IllegalArgumentException("Nenhuma imagem foi classificada com sucesso")
        }

        if (validResults.isEmpty()) {
            val nonPlantConfidence = successfulResults
                .filter { isNonPlantPrediction(it.predictedSpecies) }
                .map { it.confidence }
                .average()
                .takeIf { !it.isNaN() }
                ?.toFloat()
                ?: 0.6f

            return@withContext MultiImageAggregationResult(
                finalPredictedSpecies = MobileNetV3Classifier.NON_PLANT_LABEL,
                aggregatedConfidence = nonPlantConfidence,
                confidenceState = ConfidenceState.ABSTAIN,
                speciesVotes = emptyMap(),
                confidencePerSpecies = emptyMap(),
                processedImages = successfulResults,
                totalImagesAnalyzed = imageResults.size,
                processingTimeMs = processingTimeMs
            )
        }

        val speciesVotes = mutableMapOf<String, Int>()
        val confidencePerSpecies = mutableMapOf<String, MutableList<Float>>()

        validResults.forEach { result ->
            val species = result.predictedSpecies
            speciesVotes[species] = (speciesVotes[species] ?: 0) + 1
            confidencePerSpecies.getOrPut(species) { mutableListOf() }.add(result.confidence)
        }

        val sortedSpecies = speciesVotes.entries
            .sortedWith(compareBy<Map.Entry<String, Int>> { -it.value }
                .thenBy { -(confidencePerSpecies[it.key]?.average() ?: 0.0) })

        val finalSpecies = sortedSpecies.firstOrNull()?.key ?: "UNKNOWN"
        val finalConfidences = confidencePerSpecies[finalSpecies] ?: emptyList()
        val aggregatedConfidence = if (finalConfidences.isNotEmpty()) {
            finalConfidences.average().toFloat()
        } else {
            0f
        }

        val topAlternative = sortedSpecies.getOrNull(1)?.key
        val topAlternativeConfidences = topAlternative?.let { confidencePerSpecies[it] } ?: emptyList()
        val topAlternativeConfidence = if (topAlternativeConfidences.isNotEmpty()) {
            topAlternativeConfidences.average().toFloat()
        } else {
            null
        }

        val policy = ConfidencePolicy()
        val confidenceState = policy.evaluate(aggregatedConfidence, topAlternativeConfidence)

        val consensusScore = if (speciesVotes.isNotEmpty()) {
            (speciesVotes[finalSpecies] ?: 0).toFloat() / validResults.size
        } else {
            0f
        }

        if (config.requireConsensus && consensusScore < config.minConsensusScore) {
            return@withContext MultiImageAggregationResult(
                finalPredictedSpecies = finalSpecies,
                aggregatedConfidence = aggregatedConfidence,
                confidenceState = ConfidenceState.AMBIGUOUS,
                speciesVotes = speciesVotes,
                confidencePerSpecies = confidencePerSpecies.mapValues { it.value.average().toFloat() },
                processedImages = validResults,
                totalImagesAnalyzed = imageResults.size,
                topAlternative = topAlternative,
                topAlternativeConfidence = topAlternativeConfidence,
                processingTimeMs = processingTimeMs
            )
        }

        return@withContext MultiImageAggregationResult(
            finalPredictedSpecies = finalSpecies,
            aggregatedConfidence = aggregatedConfidence,
            confidenceState = confidenceState,
            speciesVotes = speciesVotes,
            confidencePerSpecies = confidencePerSpecies.mapValues { it.value.average().toFloat() },
            processedImages = validResults,
            totalImagesAnalyzed = imageResults.size,
            topAlternative = topAlternative,
            topAlternativeConfidence = topAlternativeConfidence,
            processingTimeMs = processingTimeMs
        )
    }

    companion object {
        private const val TAG = "PlantRepository"
        private const val LOW_CONFIDENCE_THRESHOLD = 0.30f
        private const val MAX_RERANK_CANDIDATES = 5
        private const val MODEL_WEIGHT = 0.60f
        private const val SIMILARITY_WEIGHT = 0.40f
        private const val MIN_RERANK_GAIN = 0.02f
    }
}

