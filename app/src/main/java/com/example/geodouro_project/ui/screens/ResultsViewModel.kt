package com.example.geodouro_project.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.geodouro_project.data.repository.PlantRepository
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.domain.model.EnrichedSpeciesData
import com.example.geodouro_project.domain.model.EnrichmentOrigin
import com.example.geodouro_project.domain.model.LocalInferenceResult
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
    val wikipediaUrl: String?,
    val photoUrl: String?
)

sealed class ResultsUiState {
    data object Idle : ResultsUiState()
    data object Loading : ResultsUiState()

    data class Success(
        val result: ResultUiModel,
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

    fun loadHybridResult(localInferenceResult: LocalInferenceResult) {
        lastInferenceResult = localInferenceResult

        viewModelScope.launch {
            _uiState.value = ResultsUiState.Loading

            val enrichmentResult = try {
                repository.enrichSpecies(localInferenceResult.predictedSpecies)
            } catch (_: Exception) {
                lastEnrichedData = null
                _uiState.value = ResultsUiState.Success(
                    result = buildUiModel(localInferenceResult, null),
                    sourceLabel = "Falha na API. A mostrar apenas resultado local."
                )
                return@launch
            }

            lastEnrichedData = enrichmentResult.data
            _uiState.value = ResultsUiState.Success(
                result = buildUiModel(localInferenceResult, enrichmentResult.data),
                sourceLabel = enrichmentOriginLabel(enrichmentResult.origin)
            )
        }
    }

    fun confirmObservation() {
        val inference = lastInferenceResult

        if (inference == null) {
            _uiState.value = ResultsUiState.Error("Sem inferencia local para guardar.")
            return
        }

        viewModelScope.launch {
            val current = _uiState.value
            if (current !is ResultsUiState.Success) {
                return@launch
            }

            val saveResult = repository.saveObservation(
                localResult = inference,
                enrichedData = lastEnrichedData
            )

            val message = when (saveResult.syncStatus) {
                ObservationSyncStatus.SYNCED -> "Observacao guardada e sincronizada."
                ObservationSyncStatus.PENDING -> "Observacao guardada localmente. Sync pendente."
                ObservationSyncStatus.FAILED -> "Observacao guardada. Sync falhou, sera tentado novamente."
            }

            _uiState.value = current.copy(saveMessage = message)
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
            wikipediaUrl = enrichedData?.wikipediaUrl,
            photoUrl = enrichedData?.photoUrl
        )
    }

    private fun enrichmentOriginLabel(origin: EnrichmentOrigin): String {
        return when (origin) {
            EnrichmentOrigin.CACHE -> "Dados enriquecidos via cache local."
            EnrichmentOrigin.NETWORK -> "Dados enriquecidos via iNaturalist online."
            EnrichmentOrigin.LOCAL_ONLY -> "Sem internet: apenas inferencia local disponivel."
        }
    }

    companion object {
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
