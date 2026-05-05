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
import com.example.geodouro_project.domain.model.ObservationSyncStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

sealed class ResultsUiState {
    data object Idle : ResultsUiState()
    data object Loading : ResultsUiState()

    data class Success(
        val result: ResultUiModel,
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
    private var confirmationInProgress = false

    fun loadHybridResult(localInferenceResult: LocalInferenceResult) {
        viewModelScope.launch {
            _uiState.value = ResultsUiState.Loading

            val isUnknownPlant = isUnknownPlantPrediction(
                localInferenceResult.predictedSpecies,
                localInferenceResult.rejectionReason
            )
            val isNonPlant = isNonPlantPrediction(localInferenceResult.predictedSpecies)

            if (isNonPlant || isUnknownPlant) {
                lastInferenceResult = localInferenceResult
                lastEnrichedData = null
                _uiState.value = ResultsUiState.Success(
                    result = buildUiModel(localInferenceResult, null),
                    sourceLabel = if (isUnknownPlant) {
                        "Planta desconhecida. Nao encontrada nas bases de dados disponiveis."
                    } else {
                        "Nao foi detetada nenhuma planta na imagem."
                    }
                )
                return@launch
            }

            val rerankedInference = runCatching {
                repository.rerankLowConfidenceInference(localInferenceResult)
            }.getOrDefault(localInferenceResult)
            val rerankApplied =
                rerankedInference.predictedSpecies != localInferenceResult.predictedSpecies

            lastInferenceResult = rerankedInference

            if (isNonPlantPrediction(rerankedInference.predictedSpecies)) {
                lastEnrichedData = null
                _uiState.value = ResultsUiState.Success(
                    result = buildUiModel(rerankedInference, null),
                    sourceLabel = "Nao foi detetada nenhuma planta na imagem."
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

    fun confirmObservation(notes: String = "", allowManualReview: Boolean = false) {
        if (confirmationInProgress) {
            return
        }

        confirmationInProgress = true
        viewModelScope.launch {
            try {
                Log.d(TAG, "confirmObservation tapped")
                val current = _uiState.value
                if (current !is ResultsUiState.Success) {
                    confirmationInProgress = false
                    return@launch
                }

                _uiState.value = current.copy(isConfirming = true)

                val inferenceToPersist = lastInferenceResult
                if (inferenceToPersist == null || inferenceToPersist.imageUri.isBlank()) {
                    _uiState.value = ResultsUiState.Error("Sem inferencia local para guardar.")
                    return@launch
                }

                val isUnknownPlant = isUnknownPlantPrediction(
                    inferenceToPersist.predictedSpecies,
                    inferenceToPersist.rejectionReason
                )
                if (isNonPlantPrediction(inferenceToPersist.predictedSpecies) ||
                    (isUnknownPlant && !allowManualReview)
                ) {
                    _uiState.value = ResultsUiState.Error(
                        if (isUnknownPlant) {
                            "Esta planta ainda nao foi identificada. Usa o envio para a administracao."
                        } else {
                            "A imagem analisada nao contem uma planta reconhecivel, por isso nao sera guardada."
                        }
                    )
                    return@launch
                }

                Log.d(
                    TAG,
                    "Saving observation imageUri=${inferenceToPersist.imageUri} species=${inferenceToPersist.predictedSpecies}"
                )
                val saveResult = repository.saveObservation(
                    localResult = inferenceToPersist,
                    enrichedData = lastEnrichedData,
                    notes = notes,
                    allowManualReview = allowManualReview,
                    syncImmediately = false
                )

                viewModelScope.launch {
                    runCatching { repository.syncPendingObservations() }
                        .onFailure { error ->
                            Log.w(TAG, "Background sync failed after local save", error)
                        }
                }

                Log.d(
                    TAG,
                    "saveObservation result observationId=${saveResult.observationId} syncStatus=${saveResult.syncStatus}"
                )
                val message = when (saveResult.syncStatus) {
                    ObservationSyncStatus.SYNCED -> if (allowManualReview) {
                        "Observacao realizada com sucesso! Enviada para a administracao e sincronizada."
                    } else {
                        "Observacao realizada com sucesso!"
                    }
                    ObservationSyncStatus.PENDING -> if (allowManualReview) {
                        "Observacao realizada com sucesso! Foi enviada localmente e ficara pendente ate haver ligacao ao backend."
                    } else {
                        "Observacao realizada com sucesso!"
                    }
                    ObservationSyncStatus.FAILED -> if (allowManualReview) {
                        "Observacao realizada com sucesso! Ficou guardada localmente para revisao manual, mas nao foi possivel contactar o backend por agora."
                    } else {
                        "Observacao realizada com sucesso! Ficou guardada localmente, mas nao foi possivel contactar o backend e tentaremos sincronizar novamente mais tarde."
                    }
                }

                _uiState.value = current.copy(saveMessage = message)
                _confirmedEvents.emit(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error while confirming observation", e)
                _uiState.value = ResultsUiState.Error(
                    "Erro ao guardar observacao: ${e.message ?: "Desconhecido"}"
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
        val isUnknownPlant = isUnknownPlantPrediction(
            localInferenceResult.predictedSpecies,
            localInferenceResult.rejectionReason
        )
        val isPlantDetected =
            !isNonPlantPrediction(localInferenceResult.predictedSpecies) && !isUnknownPlant

        val scientificName = if (isPlantDetected) {
            enrichedData?.scientificName ?: localInferenceResult.predictedSpecies
        } else if (isUnknownPlant) {
            MobileNetV3Classifier.UNKNOWN_PLANT_LABEL
        } else {
            MobileNetV3Classifier.NON_PLANT_LABEL
        }

        val commonName = when {
            isPlantDetected -> enrichedData?.commonName ?: "Nome comum indisponivel"
            isUnknownPlant -> "Esta observacao sera enviada automaticamente para a administracao."
            else -> "Objeto nao identificado como planta"
        }

        val family = when {
            isPlantDetected -> enrichedData?.family ?: "Familia indisponivel"
            isUnknownPlant -> "Familia desconhecida"
            else -> "Sem familia botanica"
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

    private fun isNonPlantPrediction(speciesName: String): Boolean {
        return speciesName.trim().equals(MobileNetV3Classifier.NON_PLANT_LABEL, ignoreCase = true)
    }

    private fun isUnknownPlantPrediction(speciesName: String, rejectionReason: String?): Boolean {
        return speciesName.trim()
            .equals(MobileNetV3Classifier.UNKNOWN_PLANT_LABEL, ignoreCase = true) ||
            rejectionReason?.equals("UNKNOWN_PLANT", ignoreCase = true) == true
    }

    private fun enrichmentOriginLabel(origin: EnrichmentOrigin, rerankApplied: Boolean): String {
        val baseLabel = when (origin) {
            EnrichmentOrigin.CACHE -> "Dados enriquecidos via cache local."
            EnrichmentOrigin.NETWORK -> "Dados enriquecidos via iNaturalist online."
            EnrichmentOrigin.LOCAL_ONLY ->
                "Sem dados remotos para esta especie. A mostrar apenas inferencia local."
        }

        return if (rerankApplied) {
            "$baseLabel Revalidado por similaridade visual devido a baixa confianca."
        } else {
            baseLabel
        }
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
