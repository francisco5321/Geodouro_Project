package com.example.geodouro_project.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.geodouro_project.ai.MobileNetV3Classifier
import com.example.geodouro_project.core.location.LocationResolver
import com.example.geodouro_project.core.storage.PersistentImageStorage
import com.example.geodouro_project.domain.model.LocalInferenceResult
import com.example.geodouro_project.domain.model.LocalPredictionCandidate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    data class MultiResult(
        val imageUris: List<String>,
        val latitude: Double?,
        val longitude: Double?
    ) : IdentifyNavigationEvent
}

class IdentifyViewModel(
    private val classifier: MobileNetV3Classifier,
    private val locationResolver: LocationResolver,
    private val imageStorage: PersistentImageStorage
) : ViewModel() {
    private var pendingCameraImageUri: Uri? = null

    private val _uiState = MutableStateFlow(
        IdentifyUiState(
            modelReady = classifier.isModelAvailable(),
            modelStatusLabel = classifier.getModelLoadDiagnostic()
                ?: "Modelo ${MobileNetV3Classifier.MODEL_DISPLAY_NAME} pronto"
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
                    locationLabel = "Sem permissao de localizacao",
                    shouldRequestLocationPermission = false
                )
            }
            emitMessage("Permissao de localizacao negada.")
        }
    }

    fun onCameraPermissionDenied() {
        emitMessage("Permissao de camera negada.")
    }

    fun refreshLocation() {
        if (!locationResolver.hasLocationPermission()) {
            _uiState.update {
                it.copy(
                    latitude = null,
                    longitude = null,
                    locationLabel = "Sem permissao de localizacao"
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
                    state.copy(capturedImageUris = state.capturedImageUris + imageUri.toString())
                }
                emitMessage("Imagem ${_uiState.value.capturedImageUris.size} capturada.")
                refreshLocation()
            }.onFailure {
                emitMessage("Falha ao guardar captura: ${it.message ?: "erro desconhecido"}")
            }
        }
    }

    fun onGallerySelection(uris: List<Uri>) {
        if (uris.isEmpty()) {
            emitMessage("Nenhuma imagem selecionada na galeria.")
            return
        }

        viewModelScope.launch {
            runCatching {
                val importedUris = uris.map { imageStorage.importFromUri(it) }
                    .filterNot { imported -> _uiState.value.capturedImageUris.contains(imported) }
                _uiState.update { state ->
                    state.copy(capturedImageUris = state.capturedImageUris + importedUris)
                }
                emitMessage("${importedUris.size} imagem(ns) adicionada(s) da galeria.")
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
                if (latestState.capturedImageUris.size >= 2) {
                    _navigation.emit(
                        IdentifyNavigationEvent.MultiResult(
                            imageUris = latestState.capturedImageUris,
                            latitude = latestState.latitude,
                            longitude = latestState.longitude
                        )
                    )
                } else {
                    val imageUri = latestState.capturedImageUris.first()
                    val bitmap = imageStorage.openInputStream(imageUri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    } ?: throw IllegalStateException("Nao foi possivel ler a imagem selecionada")
                    val prediction = classifier.classify(bitmap)

                    if (!prediction.fromModel) {
                        val diagnostic = classifier.getModelLoadDiagnostic()
                        emitMessage(
                            if (diagnostic.isNullOrBlank()) {
                                "Modelo ${MobileNetV3Classifier.MODEL_DISPLAY_NAME} ainda indisponivel. Resultado de fallback aplicado."
                            } else {
                                "Modelo ${MobileNetV3Classifier.MODEL_DISPLAY_NAME} indisponivel: $diagnostic"
                            }
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
                                }
                            )
                        )
                    )
                }
            }.onFailure {
                emitMessage("Falha na identificacao local: ${it.message ?: "erro desconhecido"}")
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
                        classifier = MobileNetV3Classifier(appContext),
                        locationResolver = LocationResolver(appContext),
                        imageStorage = PersistentImageStorage(appContext)
                    ) as T
                }
            }
    }
}
