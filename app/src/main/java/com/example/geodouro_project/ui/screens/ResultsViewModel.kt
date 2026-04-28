package com.example.geodouro_project.ui.screens

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.geodouro_project.ai.MobileNetV3Classifier
import com.example.geodouro_project.data.repository.PlantRepository
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.domain.model.EnrichedSpeciesData
import com.example.geodouro_project.domain.model.EnrichmentOrigin
import com.example.geodouro_project.domain.model.LocalInferenceResult
import com.example.geodouro_project.domain.model.LocalPredictionCandidate
import com.example.geodouro_project.domain.model.MultiImageAggregationConfig
import com.example.geodouro_project.domain.model.MultiImageAggregationResult
import com.example.geodouro_project.domain.model.ObservationSyncStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResultUiModel(
    val scientificName: String,
    val commonName: String,
    val family: String,
    val confidence: Float,
    val capturedImageUri: String,
    val wikipediaUrl: String?,
    val photoUrl: String?,
    val alternativePredictions: List<LocalPredictionCandidate>,
    val latitude: Double?,
    val longitude: Double?,
    val isPlantDetected: Boolean,
    val isUnknownPlant: Boolean
)

data class MultiImageResultUiModel(
    val finalSpecies: String,
    val commonName: String,
    val family: String,
    val aggregatedConfidence: Float,
    val imagesCount: Int,
    val consensus: Float,
    val topAlternative: String?,
    val topAlternativeConfidence: Float?,
    val wikipediaUrl: String?,
    val photoUrl: String?,
    val imageUris: List<String>,
    val processingTimeMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val isPlantDetected: Boolean,
    val isUnknownPlant: Boolean
)

sealed class ResultsUiState {
    data object Idle : ResultsUiState()
    data object Loading : ResultsUiState()

    data class Success(
        val result: ResultUiModel,
        val sourceLabel: String,
        val saveMessage: String? = null,
        val isConfirming: Boolean = false
    ) : ResultsUiState()

    data class MultiImageSuccess(
        val result: MultiImageResultUiModel,
        val sourceLabel: String,
        val saveMessage: String? = null,
        val isConfirming: Boolean = false
    ) : ResultsUiState()

    data class Error(
        val message: String
    ) : ResultsUiState()
}

class ResultsViewModel(
    private val repository: PlantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ResultsUiState>(ResultsUiState.Idle)
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    private val _confirmedEvents = MutableSharedFlow<Unit>()
    val confirmedEvents: SharedFlow<Unit> = _confirmedEvents.asSharedFlow()

    private var lastInferenceResult: LocalInferenceResult? = null
    private var lastEnrichedData: EnrichedSpeciesData? = null
    private var lastMultiImageResult: MultiImageAggregationResult? = null
    private var lastCaptureLatitude: Double? = null
    private var lastCaptureLongitude: Double? = null
    private var confirmationInProgress = false

    fun loadHybridResult(localInferenceResult: LocalInferenceResult) {
        viewModelScope.launch {
            _uiState.value = ResultsUiState.Loading
            lastCaptureLatitude = localInferenceResult.latitude
            lastCaptureLongitude = localInferenceResult.longitude

            val isUnknownPlant = isUnknownPlantPrediction(localInferenceResult.predictedSpecies, localInferenceResult.rejectionReason)
            val isNonPlant = isNonPlantPrediction(localInferenceResult.predictedSpecies)

            if (isNonPlant || isUnknownPlant) {
                lastInferenceResult = localInferenceResult
                lastEnrichedData = null
                val sourceLabel = when {
                    isUnknownPlant -> "Planta desconhecida. Não encontrada nas bases de dados disponíveis."
                    else -> "Não foi detetada nenhuma planta na imagem."
                }
                _uiState.value = ResultsUiState.Success(
                    result = buildUiModel(localInferenceResult, null),
                    sourceLabel = sourceLabel
                )
                return@launch
            }

            val rerankedInference = runCatching {
                repository.rerankLowConfidenceInference(localInferenceResult)
            }.getOrDefault(localInferenceResult)
            val rerankApplied = rerankedInference.predictedSpecies != localInferenceResult.predictedSpecies

            lastInferenceResult = rerankedInference
            lastCaptureLatitude = rerankedInference.latitude
            lastCaptureLongitude = rerankedInference.longitude

            if (isNonPlantPrediction(rerankedInference.predictedSpecies)) {
                lastEnrichedData = null
                _uiState.value = ResultsUiState.Success(
                    result = buildUiModel(rerankedInference, null),
                    sourceLabel = "Não foi detetada nenhuma planta na imagem."
                )
                return@launch
            }

            val enrichmentResult = try {
                repository.enrichSpecies(rerankedInference.predictedSpecies)
            } catch (_: Exception) {
                lastEnrichedData = null
                _uiState.value = ResultsUiState.Success(
                    result = buildUiModel(rerankedInference, null),
                    sourceLabel = "Falha na API. A mostrar apenas resultado local."
                )
                return@launch
            }

            lastEnrichedData = enrichmentResult.data
            _uiState.value = ResultsUiState.Success(
                result = buildUiModel(rerankedInference, enrichmentResult.data),
                sourceLabel = enrichmentOriginLabel(
                    origin = enrichmentResult.origin,
                    rerankApplied = rerankApplied
                )
            )
        }
    }

    fun loadMultiImageResult(
        imageUris: List<String>,
        latitude: Double? = null,
        longitude: Double? = null,
        config: MultiImageAggregationConfig = MultiImageAggregationConfig()
    ) {
        viewModelScope.launch {
            _uiState.value = ResultsUiState.Loading
            lastCaptureLatitude = latitude
            lastCaptureLongitude = longitude

            val aggregationResult = try {
                repository.inferMultipleImages(imageUris, config)
            } catch (e: Exception) {
                _uiState.value = ResultsUiState.Error(
                    message = "Erro ao processar multiplas imagens: ${e.message ?: "Desconhecido"}"
                )
                return@launch
            }

            lastMultiImageResult = aggregationResult

            if (isNonPlantPrediction(aggregationResult.finalPredictedSpecies) ||
                isUnknownPlantPrediction(aggregationResult.finalPredictedSpecies, null)
            ) {
                lastEnrichedData = null
                _uiState.value = ResultsUiState.MultiImageSuccess(
                    result = buildMultiImageUiModel(aggregationResult, null),
                    sourceLabel = if (isUnknownPlantPrediction(aggregationResult.finalPredictedSpecies, null)) {
                        "Foi detetada uma planta, mas ainda não a conseguimos identificar automaticamente."
                    } else {
                        "Nenhuma das imagens apresentou uma planta reconhecivel."
                    }
                )
                return@launch
            }

            val enrichmentResult = try {
                repository.enrichSpecies(aggregationResult.finalPredictedSpecies)
            } catch (_: Exception) {
                lastEnrichedData = null
                _uiState.value = ResultsUiState.MultiImageSuccess(
                    result = buildMultiImageUiModel(aggregationResult, null),
                    sourceLabel = "Falha na API. A mostrar apenas resultado local."
                )
                return@launch
            }

            lastEnrichedData = enrichmentResult.data
            _uiState.value = ResultsUiState.MultiImageSuccess(
                result = buildMultiImageUiModel(aggregationResult, enrichmentResult.data),
                sourceLabel = multiImageSourceLabel(aggregationResult)
            )
        }
    }

    fun confirmObservation(notes: String = "", allowManualReview: Boolean = false) {
        if (confirmationInProgress) {
            return
        }

        confirmationInProgress = true
        viewModelScope.launch {
            try {
                Log.d(TAG, "confirmObservation tapped")
                val current = _uiState.value

                _uiState.value = when (current) {
                    is ResultsUiState.Success -> current.copy(isConfirming = true)
                    is ResultsUiState.MultiImageSuccess -> current.copy(isConfirming = true)
                    else -> {
                        confirmationInProgress = false
                        return@launch
                    }
                }

                val inferenceToPersist = when (current) {
                    is ResultsUiState.Success -> {
                        lastInferenceResult
                    }
                    is ResultsUiState.MultiImageSuccess -> {
                        val multi = lastMultiImageResult
                        if (multi == null) {
                            _uiState.value = ResultsUiState.Error("Sem resultado multi-imagem para guardar.")
                            return@launch
                        }

                        LocalInferenceResult(
                            imageUri = multi.processedImages.firstOrNull()?.imageUri.orEmpty(),
                            latitude = lastCaptureLatitude,
                            longitude = lastCaptureLongitude,
                            predictedSpecies = multi.finalPredictedSpecies,
                            confidence = multi.aggregatedConfidence,
                            candidatePredictions = buildList {
                                add(
                                    LocalPredictionCandidate(
                                        species = multi.finalPredictedSpecies,
                                        confidence = multi.aggregatedConfidence
                                    )
                                )
                                if (!multi.topAlternative.isNullOrBlank() && multi.topAlternativeConfidence != null) {
                                    add(
                                        LocalPredictionCandidate(
                                            species = multi.topAlternative,
                                            confidence = multi.topAlternativeConfidence
                                        )
                                    )
                                }
                            }
                        )
                    }
                    else -> return@launch
                }

                if (inferenceToPersist == null || inferenceToPersist.imageUri.isBlank()) {
                    _uiState.value = ResultsUiState.Error("Sem inferencia local para guardar.")
                    return@launch
                }

                val isUnknownPlant = isUnknownPlantPrediction(
                    inferenceToPersist.predictedSpecies,
                    inferenceToPersist.rejectionReason
                )
                if (isNonPlantPrediction(inferenceToPersist.predictedSpecies) ||
                    (isUnknownPlant && !allowManualReview)) {
                    _uiState.value = ResultsUiState.Error(
                        if (isUnknownPlant) {
                            "Esta planta ainda não foi identificada. Usa o envio para a administração."
                        } else {
                            "A imagem analisada não contem uma planta reconhecivel, por isso não sera guardada."
                        }
                    )
                    return@launch
                }

                val imageUrisToPersist = when (current) {
                    is ResultsUiState.MultiImageSuccess -> lastMultiImageResult
                        ?.processedImages
                        ?.map { it.imageUri }
                        ?.filter { it.isNotBlank() }
                        ?.distinct()
                        .orEmpty()
                    else -> listOf(inferenceToPersist.imageUri)
                }

                Log.d(
                    TAG,
                    "Saving observation imageUri=${inferenceToPersist.imageUri} species=${inferenceToPersist.predictedSpecies} imageCount=${imageUrisToPersist.size}"
                )
                val saveResult = repository.saveObservation(
                    localResult = inferenceToPersist,
                    enrichedData = lastEnrichedData,
                    imageUris = imageUrisToPersist,
                    notes = notes,
                    allowManualReview = allowManualReview
                )

                Log.d(TAG, "saveObservation result observationId=${saveResult.observationId} syncStatus=${saveResult.syncStatus}")
                val message = when (saveResult.syncStatus) {
                    ObservationSyncStatus.SYNCED -> if (allowManualReview) {
                        "Observação enviada para a administração e sincronizada."
                    } else {
                        "Observação guardada e sincronizada."
                    }
                    ObservationSyncStatus.PENDING -> {
                        if (allowManualReview) {
                            "Observação enviada localmente. Ficará pendente até haver ligação ao backend."
                        } else {
                            "Observação guardada localmente. A sincronização fica pendente até haver ligação ao backend."
                        }
                    }
                    ObservationSyncStatus.FAILED -> {
                        if (allowManualReview) {
                            "Observação guardada localmente para revisão manual. Não foi possível contactar o backend por agora."
                        } else {
                            "Observação guardada localmente. Não foi possível contactar o backend, por isso tentaremos sincronizar novamente mais tarde."
                        }
                    }
                }

                _uiState.value = when (current) {
                    is ResultsUiState.Success -> current.copy(saveMessage = message)
                    is ResultsUiState.MultiImageSuccess -> current.copy(saveMessage = message)
                    else -> current
                }
                _confirmedEvents.emit(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error while confirming observation", e)
                _uiState.value = ResultsUiState.Error(
                    "Erro ao guardar observação: ${e.message ?: "Desconhecido"}"
                )
            } finally {
                confirmationInProgress = false
            }
        }
    }

    private fun buildUiModel(
        localInferenceResult: LocalInferenceResult,
        enrichedData: EnrichedSpeciesData?
    ): ResultUiModel {
        val isPlantDetected = !isNonPlantPrediction(localInferenceResult.predictedSpecies) && 
                              !isUnknownPlantPrediction(localInferenceResult.predictedSpecies, localInferenceResult.rejectionReason)
        val isUnknownPlant = isUnknownPlantPrediction(localInferenceResult.predictedSpecies, localInferenceResult.rejectionReason)
        
        val scientificName = if (isPlantDetected) {
            enrichedData?.scientificName ?: localInferenceResult.predictedSpecies
        } else if (isUnknownPlant) {
            MobileNetV3Classifier.UNKNOWN_PLANT_LABEL
        } else {
            MobileNetV3Classifier.NON_PLANT_LABEL
        }

        val commonName = when {
            isPlantDetected -> enrichedData?.commonName ?: "Nome comum indisponivel"
            isUnknownPlant -> "Pretende enviar à administração para melhor análise?"
            else -> "Objeto não identificado como planta"
        }

        val family = when {
            isPlantDetected -> enrichedData?.family ?: "Família indisponível"
            isUnknownPlant -> "Família desconhecida"
            else -> "Sem família botânica"
        }

        return ResultUiModel(
            scientificName = scientificName,
            commonName = commonName,
            family = family,
            confidence = localInferenceResult.confidence,
            capturedImageUri = localInferenceResult.imageUri,
            wikipediaUrl = if (isPlantDetected) enrichedData?.wikipediaUrl else null,
            photoUrl = if (isPlantDetected) enrichedData?.photoUrl else null,
            alternativePredictions = if (isPlantDetected) {
                localInferenceResult.candidatePredictions
                    .drop(1)
                    .filter { it.confidence >= DISPLAY_ALTERNATIVE_THRESHOLD }
            } else {
                emptyList()
            },
            latitude = localInferenceResult.latitude,
            longitude = localInferenceResult.longitude,
            isPlantDetected = isPlantDetected,
            isUnknownPlant = isUnknownPlant
        )
    }

    private fun buildMultiImageUiModel(
        multiImageResult: MultiImageAggregationResult,
        enrichedData: EnrichedSpeciesData?
    ): MultiImageResultUiModel {
        val isUnknownPlant = isUnknownPlantPrediction(multiImageResult.finalPredictedSpecies, null)
        val isPlantDetected = !isNonPlantPrediction(multiImageResult.finalPredictedSpecies) && !isUnknownPlant
        return MultiImageResultUiModel(
            finalSpecies = if (isPlantDetected) {
                enrichedData?.scientificName ?: multiImageResult.finalPredictedSpecies
            } else if (isUnknownPlant) {
                MobileNetV3Classifier.UNKNOWN_PLANT_LABEL
            } else {
                MobileNetV3Classifier.NON_PLANT_LABEL
            },
            commonName = if (isPlantDetected) {
                enrichedData?.commonName ?: "Nome comum indisponível"
            } else if (isUnknownPlant) {
                "Pretende enviar a observação para a administração?"
            } else {
                "Nenhuma planta reconhecida nas imagens"
            },
            family = if (isPlantDetected) {
                enrichedData?.family ?: "Família indisponível"
            } else if (isUnknownPlant) {
                "Família desconhecida"
            } else {
                "Sem família botânica"
            },
            aggregatedConfidence = multiImageResult.aggregatedConfidence,
            imagesCount = multiImageResult.totalImagesAnalyzed,
            consensus = multiImageResult.consensusScore,
            topAlternative = if (isPlantDetected) multiImageResult.topAlternative else null,
            topAlternativeConfidence = if (isPlantDetected) multiImageResult.topAlternativeConfidence else null,
            wikipediaUrl = if (isPlantDetected) enrichedData?.wikipediaUrl else null,
            photoUrl = if (isPlantDetected) enrichedData?.photoUrl else null,
            imageUris = multiImageResult.processedImages.map { it.imageUri },
            processingTimeMs = multiImageResult.processingTimeMs,
            latitude = lastCaptureLatitude,
            longitude = lastCaptureLongitude,
            isPlantDetected = isPlantDetected,
            isUnknownPlant = isUnknownPlant
        )
    }

    private fun isNonPlantPrediction(speciesName: String): Boolean {
        return speciesName.trim().equals(MobileNetV3Classifier.NON_PLANT_LABEL, ignoreCase = true)
    }

    private fun isUnknownPlantPrediction(speciesName: String, rejectionReason: String?): Boolean {
        return speciesName.trim().equals(MobileNetV3Classifier.UNKNOWN_PLANT_LABEL, ignoreCase = true) ||
               rejectionReason?.equals("UNKNOWN_PLANT", ignoreCase = true) == true
    }

    private fun enrichmentOriginLabel(origin: EnrichmentOrigin, rerankApplied: Boolean): String {
        val baseLabel = when (origin) {
            EnrichmentOrigin.CACHE -> "Dados enriquecidos via cache local."
            EnrichmentOrigin.NETWORK -> "Dados enriquecidos via iNaturalist online."
            EnrichmentOrigin.LOCAL_ONLY -> "Sem dados remotos para esta espécie. A mostrar apenas inferência local."
        }

        return if (rerankApplied) {
            "$baseLabel Revalidado por similaridade visual devido a baixa confiança."
        } else {
            baseLabel
        }
    }

    private fun multiImageSourceLabel(result: MultiImageAggregationResult): String {
        val baseLabel = when {
            result.isUnanimous -> "Consenso total: todas as ${result.totalImagesAnalyzed} imagens concordam."
            result.consensusScore >= 0.8f -> "Consenso forte: ${(result.consensusScore * 100).toInt()}% das imagens."
            result.consensusScore >= 0.6f -> "Consenso moderado: ${(result.consensusScore * 100).toInt()}% das imagens."
            else -> "Consenso baixo: ${(result.consensusScore * 100).toInt()}% das imagens."
        }

        return "$baseLabel\nTempo de processamento: ${result.processingTimeMs}ms"
    }

    companion object {
        private const val DISPLAY_ALTERNATIVE_THRESHOLD = 0.30f
        private const val TAG = "ResultsViewModel"

        fun factory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val repository = AppContainer.providePlantRepository(context)
                    return ResultsViewModel(repository) as T
                }
            }
        }
    }
}
