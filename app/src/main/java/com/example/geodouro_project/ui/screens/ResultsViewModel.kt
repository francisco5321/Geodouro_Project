package com.example.geodouro_project.ui.screens

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.geodouro_project.data.repository.PlantRepository
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.domain.model.EnrichedSpeciesData
import com.example.geodouro_project.domain.model.EnrichmentOrigin
import com.example.geodouro_project.domain.model.LocalInferenceResult
import com.example.geodouro_project.domain.model.LocalPredictionCandidate
import com.example.geodouro_project.domain.model.MultiImageAggregationConfig
import com.example.geodouro_project.domain.model.MultiImageAggregationResult
import com.example.geodouro_project.domain.model.ObservationSyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
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
    val alternativePredictions: List<LocalPredictionCandidate>
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
    val processingTimeMs: Long
)

sealed class ResultsUiState {
    data object Idle : ResultsUiState()
    data object Loading : ResultsUiState()

    data class Success(
        val result: ResultUiModel,
        val sourceLabel: String,
        val saveMessage: String? = null
    ) : ResultsUiState()

    data class MultiImageSuccess(
        val result: MultiImageResultUiModel,
        val sourceLabel: String,
        val saveMessage: String? = null
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

    private var lastInferenceResult: LocalInferenceResult? = null
    private var lastEnrichedData: EnrichedSpeciesData? = null
    private var lastMultiImageResult: MultiImageAggregationResult? = null

    fun loadHybridResult(localInferenceResult: LocalInferenceResult) {
        viewModelScope.launch {
            _uiState.value = ResultsUiState.Loading

            val rerankedInference = runCatching {
                repository.rerankLowConfidenceInference(localInferenceResult)
            }.getOrDefault(localInferenceResult)
            val rerankApplied = rerankedInference.predictedSpecies != localInferenceResult.predictedSpecies

            lastInferenceResult = rerankedInference

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
        config: MultiImageAggregationConfig = MultiImageAggregationConfig()
    ) {
        viewModelScope.launch {
            _uiState.value = ResultsUiState.Loading

            val aggregationResult = try {
                repository.inferMultipleImages(imageUris, config)
            } catch (e: Exception) {
                _uiState.value = ResultsUiState.Error(
                    message = "Erro ao processar multiplas imagens: ${e.message ?: "Desconhecido"}"
                )
                return@launch
            }

            lastMultiImageResult = aggregationResult

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

    fun confirmObservation() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "confirmObservation tapped")
                val current = _uiState.value

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
                            latitude = null,
                            longitude = null,
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

                Log.d(TAG, "Saving observation imageUri=${inferenceToPersist.imageUri} species=${inferenceToPersist.predictedSpecies}")
                val saveResult = repository.saveObservation(
                    localResult = inferenceToPersist,
                    enrichedData = lastEnrichedData
                )

                Log.d(TAG, "saveObservation result observationId=${saveResult.observationId} syncStatus=${saveResult.syncStatus}")
                val message = when (saveResult.syncStatus) {
                    ObservationSyncStatus.SYNCED -> "Observacao guardada e sincronizada."
                    ObservationSyncStatus.PENDING -> "Observacao guardada localmente. Sync pendente."
                    ObservationSyncStatus.FAILED -> "Observacao guardada. Sync falhou, sera tentado novamente."
                }

                _uiState.value = when (current) {
                    is ResultsUiState.Success -> current.copy(saveMessage = message)
                    is ResultsUiState.MultiImageSuccess -> current.copy(saveMessage = message)
                    else -> current
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error while confirming observation", e)
                _uiState.value = ResultsUiState.Error(
                    "Erro ao guardar observacao: ${e.message ?: "Desconhecido"}"
                )
            }
        }
    }

    fun syncPendingObservations() {
        viewModelScope.launch {
            repository.syncPendingObservations()
        }
    }

    private fun buildUiModel(
        localInferenceResult: LocalInferenceResult,
        enrichedData: EnrichedSpeciesData?
    ): ResultUiModel {
        val scientificName = enrichedData?.scientificName ?: localInferenceResult.predictedSpecies

        return ResultUiModel(
            scientificName = scientificName,
            commonName = enrichedData?.commonName ?: "Nome comum indisponivel",
            family = enrichedData?.family ?: "Familia indisponivel",
            confidence = localInferenceResult.confidence,
            capturedImageUri = localInferenceResult.imageUri,
            wikipediaUrl = enrichedData?.wikipediaUrl,
            photoUrl = enrichedData?.photoUrl,
            alternativePredictions = localInferenceResult.candidatePredictions
                .drop(1)
                .filter { it.confidence >= DISPLAY_ALTERNATIVE_THRESHOLD }
        )
    }

    private fun buildMultiImageUiModel(
        multiImageResult: MultiImageAggregationResult,
        enrichedData: EnrichedSpeciesData?
    ): MultiImageResultUiModel {
        return MultiImageResultUiModel(
            finalSpecies = enrichedData?.scientificName ?: multiImageResult.finalPredictedSpecies,
            commonName = enrichedData?.commonName ?: "Nome comum indisponivel",
            family = enrichedData?.family ?: "Familia indisponivel",
            aggregatedConfidence = multiImageResult.aggregatedConfidence,
            imagesCount = multiImageResult.totalImagesAnalyzed,
            consensus = multiImageResult.consensusScore,
            topAlternative = multiImageResult.topAlternative,
            topAlternativeConfidence = multiImageResult.topAlternativeConfidence,
            wikipediaUrl = enrichedData?.wikipediaUrl,
            photoUrl = enrichedData?.photoUrl,
            imageUris = multiImageResult.processedImages.map { it.imageUri },
            processingTimeMs = multiImageResult.processingTimeMs
        )
    }

    private fun enrichmentOriginLabel(origin: EnrichmentOrigin, rerankApplied: Boolean): String {
        val baseLabel = when (origin) {
            EnrichmentOrigin.CACHE -> "Dados enriquecidos via cache local."
            EnrichmentOrigin.NETWORK -> "Dados enriquecidos via iNaturalist online."
            EnrichmentOrigin.LOCAL_ONLY -> "Sem dados remotos para esta especie. A mostrar apenas inferencia local."
        }

        return if (rerankApplied) {
            "$baseLabel Revalidado por similaridade visual devido a baixa confianca."
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
