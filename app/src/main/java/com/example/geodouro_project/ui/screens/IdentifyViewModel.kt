package com.example.geodouro_project.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.geodouro_project.ai.PlantInferenceEngine
import com.example.geodouro_project.core.location.LocationResolver
import com.example.geodouro_project.core.storage.PersistentImageStorage
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.domain.model.LocalInferenceResult
import com.example.geodouro_project.domain.model.LocalPredictionCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class IdentifyUiState(
    val isProcessing: Boolean = false,
    val capturedImageUris: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String = "A obter localizacao GPS...",
    val modelReady: Boolean = false,
    val modelStatusLabel: String = "",
    val shouldRequestLocationPermission: Boolean = false
)

sealed interface IdentifyNavigationEvent {
    data class SingleResult(val result: LocalInferenceResult) : IdentifyNavigationEvent
}

class IdentifyViewModel(
    private val inferenceEngine: PlantInferenceEngine,
    private val locationResolver: LocationResolver,
    private val imageStorage: PersistentImageStorage
) : ViewModel() {
    private var pendingCameraImageUri: Uri? = null

    private val _uiState = MutableStateFlow(
        IdentifyUiState(
            modelReady = inferenceEngine.isModelAvailable(),
            modelStatusLabel = inferenceEngine.getModelStatusLabel()
        )
    )
    val uiState: StateFlow<IdentifyUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _navigation = MutableSharedFlow<IdentifyNavigationEvent>()
    val navigation: SharedFlow<IdentifyNavigationEvent> = _navigation.asSharedFlow()

    fun requestInitialLocation() {
        if (locationResolver.hasLocationPermission()) {
            refreshLocation()
        } else {
            _uiState.update { it.copy(shouldRequestLocationPermission = true) }
        }
    }

    fun onLocationPermissionRequestHandled() {
        _uiState.update { it.copy(shouldRequestLocationPermission = false) }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) {
            refreshLocation()
        } else {
            _uiState.update {
                it.copy(
                    latitude = null,
                    longitude = null,
                    locationLabel = "Sem permissão de localização",
                    shouldRequestLocationPermission = false
                )
            }
            emitMessage("Permissão de localização negada.")
        }
    }

    fun onCameraPermissionDenied() {
        emitMessage("Permissão de câmara negada.")
    }

    fun refreshLocation() {
        if (!locationResolver.hasLocationPermission()) {
            _uiState.update {
                it.copy(
                    latitude = null,
                    longitude = null,
                    locationLabel = "Sem permissão de localização"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(locationLabel = "A obter localizacao GPS...") }
            val coordinates = locationResolver.getCurrentCoordinates()
            _uiState.update { current ->
                if (coordinates != null) {
                    current.copy(
                        latitude = coordinates.first,
                        longitude = coordinates.second,
                        locationLabel = "GPS: %.6f, %.6f".format(coordinates.first, coordinates.second)
                    )
                } else {
                    current.copy(
                        latitude = null,
                        longitude = null,
                        locationLabel = "Localizacao indisponivel"
                    )
                }
            }
        }
    }

    fun prepareCameraCapture(): Uri? {
        return runCatching {
            imageStorage.createCameraImageUri()
        }.onSuccess { uri ->
            pendingCameraImageUri = uri
        }.onFailure {
            emitMessage("Falha ao preparar captura: ${it.message ?: "erro desconhecido"}")
        }.getOrNull()
    }

    fun onCameraCaptureResult(saved: Boolean) {
        val imageUri = pendingCameraImageUri
        pendingCameraImageUri = null

        if (!saved || imageUri == null) {
            emitMessage("Captura cancelada.")
            return
        }

        viewModelScope.launch {
            runCatching {
                _uiState.update { state ->
                    state.copy(capturedImageUris = listOf(imageUri.toString()))
                }
                emitMessage("Imagem capturada.")
                refreshLocation()
            }.onFailure {
                emitMessage("Falha ao guardar captura: ${it.message ?: "erro desconhecido"}")
            }
        }
    }

    fun onGallerySelection(uri: Uri?) {
        if (uri == null) {
            emitMessage("Nenhuma imagem selecionada na galeria.")
            return
        }

        viewModelScope.launch {
            runCatching {
                val importedUri = withContext(Dispatchers.IO) {
                    imageStorage.importFromUri(uri)
                }
                _uiState.update { state ->
                    state.copy(capturedImageUris = listOf(importedUri))
                }
                emitMessage("Imagem adicionada da galeria.")
                refreshLocation()
            }.onFailure {
                emitMessage("Falha ao importar imagens: ${it.message ?: "erro desconhecido"}")
            }
        }
    }

    fun clearCaptures() {
        _uiState.update { it.copy(capturedImageUris = emptyList()) }
    }

    fun analyzeSelection() {
        val currentState = _uiState.value
        if (currentState.isProcessing || currentState.capturedImageUris.isEmpty()) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            runCatching {
                val resolvedCoordinates = resolveCoordinates()
                if (resolvedCoordinates != null) {
                    _uiState.update {
                        it.copy(
                            latitude = resolvedCoordinates.first,
                            longitude = resolvedCoordinates.second,
                            locationLabel = "GPS: %.6f, %.6f".format(
                                resolvedCoordinates.first,
                                resolvedCoordinates.second
                            )
                        )
                    }
                }

                val latestState = _uiState.value
                val imageUri = latestState.capturedImageUris.first()
                val bitmap = withContext(Dispatchers.IO) {
                    imageStorage.decodeSampledBitmap(imageUri)
                } ?: throw IllegalStateException("Não foi possível ler a imagem selecionada")
                val prediction = inferenceEngine.classify(bitmap)

                if (!prediction.fromModel) {
                    emitMessage(
                        "Pipeline de inferência indisponível. Resultado de fallback aplicado."
                    )
                }

                _navigation.emit(
                    IdentifyNavigationEvent.SingleResult(
                        LocalInferenceResult(
                            imageUri = imageUri,
                            latitude = latestState.latitude,
                            longitude = latestState.longitude,
                            predictedSpecies = prediction.label,
                            confidence = prediction.confidence,
                            candidatePredictions = prediction.candidates.map { candidate ->
                                LocalPredictionCandidate(
                                    species = candidate.label,
                                    confidence = candidate.confidence
                                )
                            },
                            rejectionReason = prediction.rejectionReason?.name
                        )
                    )
                )
            }.onFailure {
                emitMessage("Falha na identificação local: ${it.message ?: "erro desconhecido"}")
            }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    private suspend fun resolveCoordinates(): Pair<Double, Double>? {
        val currentState = _uiState.value
        if (currentState.latitude != null && currentState.longitude != null) {
            return currentState.latitude to currentState.longitude
        }

        if (!locationResolver.hasLocationPermission()) {
            return null
        }

        return withTimeoutOrNull(2_000) {
            locationResolver.getCurrentCoordinates()
        }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _messages.emit(message)
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    return IdentifyViewModel(
                        inferenceEngine = AppContainer.providePlantInferenceEngine(appContext),
                        locationResolver = LocationResolver(appContext),
                        imageStorage = PersistentImageStorage(appContext)
                    ) as T
                }
            }
    }
}
