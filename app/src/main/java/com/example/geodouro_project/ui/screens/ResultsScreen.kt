package com.example.geodouro_project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.geodouro_project.domain.model.LocalInferenceResult
import com.example.geodouro_project.domain.model.LocalPredictionCandidate
import com.example.geodouro_project.ui.components.GeoFloraHeaderLogo
import com.example.geodouro_project.ui.theme.GeodouroBg
import com.example.geodouro_project.ui.theme.GeodouroBrandGreen
import com.example.geodouro_project.ui.theme.GeodouroCardBg
import com.example.geodouro_project.ui.theme.GeodouroError
import com.example.geodouro_project.ui.theme.GeodouroGreen
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
import com.example.geodouro_project.ui.theme.geodouroOutlinedBorderColor
import com.example.geodouro_project.ui.theme.geodouroOutlinedButtonColors
import com.example.geodouro_project.ui.theme.geodouroOutlinedTextFieldColors
import com.example.geodouro_project.ui.theme.geodouroPrimaryButtonColors

data class IdentificationResult(
    val scientificName: String,
    val commonName: String,
    val family: String,
    val confidence: Float,
    val sourceLabel: String,
    val wikipediaUrl: String?,
    val photoUrl: String?
)

enum class PredictionFeedback {
    LIKE,
    DISLIKE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    refreshTrigger: Int = 0,
    onBackClick: () -> Unit,
    onRetakePhotosClick: () -> Unit = onBackClick,
    onConfirmResult: (IdentificationResult, String?) -> Unit,
    captureLatitude: Double? = null,
    captureLongitude: Double? = null,
    localInferenceResult: LocalInferenceResult = LocalInferenceResult(
        imageUri = "",
        latitude = null,
        longitude = null,
        predictedSpecies = "Sem inferência local",
        confidence = 0f
    )
) {
    val context = LocalContext.current
    val viewModel: ResultsViewModel = viewModel(
        factory = ResultsViewModel.factory(context.applicationContext)
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var observationNotes by rememberSaveable(localInferenceResult.imageUri) { mutableStateOf("") }
    var predictionFeedback by rememberSaveable(localInferenceResult.imageUri) {
        mutableStateOf<PredictionFeedback?>(null)
    }
    var showUnknownPlantDialog by rememberSaveable(localInferenceResult.imageUri) {
        mutableStateOf(false)
    }
    var unknownPlantDialogHandled by rememberSaveable(localInferenceResult.imageUri) {
        mutableStateOf(false)
    }

    LaunchedEffect(localInferenceResult, captureLatitude, captureLongitude) {
        viewModel.loadHybridResult(localInferenceResult)
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            viewModel.loadHybridResult(localInferenceResult)
        }
    }

    LaunchedEffect(uiState) {
        val state = uiState
        showUnknownPlantDialog = state is ResultsUiState.Success &&
            state.result.isUnknownPlant &&
            state.saveMessage.isNullOrBlank() &&
            !state.isConfirming &&
            !unknownPlantDialogHandled
    }

    LaunchedEffect(viewModel) {
        viewModel.confirmedEvents.collect {
            val state = uiState
            if (state is ResultsUiState.Success) {
                onConfirmResult(
                    state.result.toIdentificationResult(state.sourceLabel),
                    state.saveMessage
                )
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                expandedHeight = 48.dp,
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Identificação de resultados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GeodouroBrandGreen
                        )
                        GeoFloraHeaderLogo()
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = GeodouroTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GeodouroBg
                )
            )
        },
        containerColor = GeodouroBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GeodouroBg)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (val state = uiState) {
                is ResultsUiState.Idle,
                is ResultsUiState.Loading -> HybridLoadingCard()

                is ResultsUiState.Error -> ErrorCard(message = state.message)

                is ResultsUiState.Success -> {
                    ResultCard(
                        result = state.result,
                        sourceLabel = state.sourceLabel,
                        saveMessage = state.saveMessage,
                        isConfirming = state.isConfirming,
                        notes = observationNotes,
                        onNotesChange = { observationNotes = it },
                        feedback = predictionFeedback,
                        onLike = { predictionFeedback = PredictionFeedback.LIKE },
                        onDislike = {
                            predictionFeedback = PredictionFeedback.DISLIKE
                            viewModel.confirmObservation(observationNotes, allowManualReview = true)
                        },
                        onConfirm = { viewModel.confirmObservation(observationNotes) },
                        onSubmitUnknownPlant = {
                            predictionFeedback = PredictionFeedback.DISLIKE
                            viewModel.confirmObservation(observationNotes, allowManualReview = true)
                        },
                        onRetakePhotos = onRetakePhotosClick
                    )
                }
            }
        }
    }

    if (showUnknownPlantDialog) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = GeodouroCardBg,
            titleContentColor = GeodouroBrandGreen,
            textContentColor = GeodouroTextPrimary,
            tonalElevation = 0.dp,
            title = {
                Text("Planta fora da base de dados")
            },
            text = {
                Text(
                    "A observação será enviada automaticamente para a administração para análise manual."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        unknownPlantDialogHandled = true
                        showUnknownPlantDialog = false
                        viewModel.confirmObservation(observationNotes, allowManualReview = true)
                    },
                    enabled = (uiState as? ResultsUiState.Success)?.isConfirming != true,
                    colors = geodouroPrimaryButtonColors()
                ) {
                    if ((uiState as? ResultsUiState.Success)?.isConfirming == true) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = GeodouroWhite
                        )
                    } else {
                        Text("Continuar")
                    }
                }
            }
        )
    }
}

@Composable
fun HybridLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "A enriquecer resultado...",
                style = MaterialTheme.typography.titleMedium,
                color = GeodouroTextPrimary,
                fontWeight = FontWeight.Bold
            )

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = GeodouroGreen,
                trackColor = GeodouroLightBg
            )
        }
    }
}

@Composable
fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = GeodouroTextPrimary,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun ResultCard(
    result: ResultUiModel,
    sourceLabel: String,
    saveMessage: String?,
    isConfirming: Boolean,
    notes: String,
    onNotesChange: (String) -> Unit,
    feedback: PredictionFeedback?,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onConfirm: () -> Unit,
    onSubmitUnknownPlant: () -> Unit,
    onRetakePhotos: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ResultPhotosSection(
                capturedImageUri = result.capturedImageUri,
                referencePhotoUrl = result.photoUrl,
                isPlantDetected = result.isPlantDetected
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = result.scientificName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GeodouroTextPrimary
            )

            Text(
                text = result.commonName,
                style = MaterialTheme.typography.bodyMedium,
                color = GeodouroTextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = result.family,
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroTextSecondary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${(result.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = GeodouroGreen,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { result.confidence },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = GeodouroGreen,
                trackColor = GeodouroLightBg
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = GeodouroLightBg,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = sourceLabel,
                    modifier = Modifier.padding(10.dp),
                    color = GeodouroTextPrimary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (!result.wikipediaUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                WikipediaLink(
                    url = result.wikipediaUrl,
                    onOpen = { uriHandler.openUri(result.wikipediaUrl) }
                )
            }

            if (result.alternativePredictions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                AlternativePredictionsSection(predictions = result.alternativePredictions)
            }

            CoordinatesSection(
                latitude = result.latitude,
                longitude = result.longitude
            )

            if (result.isPlantDetected || result.isUnknownPlant) {
                Spacer(modifier = Modifier.height(12.dp))
                ObservationNotesField(
                    value = notes,
                    onValueChange = onNotesChange
                )
            }

            if (!saveMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = GeodouroGreen
                    )
                    Text(
                        text = saveMessage,
                        color = GeodouroTextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = when {
                    result.isPlantDetected -> onConfirm
                    result.isUnknownPlant -> onSubmitUnknownPlant
                    else -> onRetakePhotos
                },
                enabled = !isConfirming,
                modifier = Modifier.fillMaxWidth(),
                colors = geodouroPrimaryButtonColors(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = when {
                        isConfirming -> "A guardar..."
                        result.isPlantDetected -> "Confirmar e guardar"
                        result.isUnknownPlant -> "A enviar para administração"
                        else -> "Tirar nova foto"
                    }
                )
            }

            if (result.isPlantDetected) {
                Spacer(modifier = Modifier.height(10.dp))
                PredictionFeedbackSection(
                    selectedFeedback = feedback,
                    enabled = !isConfirming,
                    onLike = onLike,
                    onDislike = onDislike
                )
            }
        }
    }
}

@Composable
private fun ResultPhotosSection(
    capturedImageUri: String,
    referencePhotoUrl: String?,
    isPlantDetected: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ResultPhotoCard(
            title = "Foto capturada",
            imageModel = capturedImageUri,
            emptyMessage = "Sem foto capturada."
        )

        if (isPlantDetected) {
            ResultPhotoCard(
                title = "Foto de referencia",
                imageModel = referencePhotoUrl,
                emptyMessage = "Sem foto remota disponível para esta espécie."
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = GeodouroLightBg,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Nenhuma planta encontrada",
                    modifier = Modifier.padding(10.dp),
                    color = GeodouroTextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ResultPhotoCard(
    title: String,
    imageModel: String?,
    emptyMessage: String
) {
    val context = LocalContext.current
    var isPreviewOpen by remember(imageModel) { mutableStateOf(false) }
    val request = remember(imageModel) {
        ImageRequest.Builder(context)
            .data(imageModel)
            .size(RESULT_IMAGE_MAX_SIZE)
            .crossfade(false)
            .build()
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = GeodouroTextPrimary
        )

        if (imageModel.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GeodouroLightBg),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = GeodouroTextSecondary
                    )
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = GeodouroTextSecondary
                    )
                }
            }
        } else {
            AsyncImage(
                model = request,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GeodouroLightBg)
                    .clickable { isPreviewOpen = true },
                contentScale = ContentScale.Crop
            )
        }
    }

    if (isPreviewOpen && !imageModel.isNullOrBlank()) {
        Dialog(
            onDismissRequest = { isPreviewOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = title,
                        color = GeodouroWhite,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    AsyncImage(
                        model = request,
                        contentDescription = "$title ampliada",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )

                    Button(
                        onClick = { isPreviewOpen = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = geodouroPrimaryButtonColors()
                    ) {
                        Text("Fechar")
                    }
                }
            }
        }
    }
}

@Composable
private fun CoordinatesSection(
    latitude: Double?,
    longitude: Double?
) {
    Spacer(modifier = Modifier.height(12.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GeodouroLightBg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = if (latitude != null && longitude != null) {
                "Coordenadas: %.6f, %.6f".format(latitude, longitude)
            } else {
                "Coordenadas: localização indisponível"
            },
            modifier = Modifier.padding(10.dp),
            color = GeodouroTextPrimary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AlternativePredictionsSection(predictions: List<LocalPredictionCandidate>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Outras previsoes do modelo acima de 30%",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = GeodouroTextPrimary
        )

        predictions.forEach { prediction ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = GeodouroLightBg,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = prediction.species,
                            style = MaterialTheme.typography.bodyMedium,
                            color = GeodouroTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(prediction.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = GeodouroGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearProgressIndicator(
                        progress = { prediction.confidence.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = GeodouroGreen,
                        trackColor = GeodouroWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun PredictionFeedbackSection(
    selectedFeedback: PredictionFeedback?,
    enabled: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val likeSelected = selectedFeedback == PredictionFeedback.LIKE
        val dislikeSelected = selectedFeedback == PredictionFeedback.DISLIKE

        OutlinedButton(
            onClick = onLike,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = geodouroOutlinedButtonColors(),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (likeSelected) GeodouroGreen else geodouroOutlinedBorderColor(enabled)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ThumbUp,
                contentDescription = "Like",
                tint = if (likeSelected) GeodouroGreen else GeodouroTextSecondary
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Like",
                color = if (likeSelected) GeodouroGreen else GeodouroTextPrimary
            )
        }

        OutlinedButton(
            onClick = onDislike,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = geodouroOutlinedButtonColors(),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (dislikeSelected) GeodouroError else geodouroOutlinedBorderColor(enabled)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ThumbDown,
                contentDescription = "Dislike",
                tint = if (dislikeSelected) GeodouroError else GeodouroTextSecondary
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Dislike",
                color = if (dislikeSelected) GeodouroError else GeodouroTextPrimary
            )
        }
    }
}

private fun ResultUiModel.toIdentificationResult(sourceLabel: String): IdentificationResult {
    return IdentificationResult(
        scientificName = scientificName,
        commonName = commonName,
        family = family,
        confidence = confidence,
        sourceLabel = sourceLabel,
        wikipediaUrl = wikipediaUrl,
        photoUrl = photoUrl
    )
}

private const val RESULT_IMAGE_MAX_SIZE = 1200

@Composable
private fun ObservationNotesField(
    value: String,
    onValueChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GeodouroLightBg,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Descrição opcional",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = GeodouroTextPrimary
            )
            Text(
                text = "Adiciona um pequeno contexto sobre a observação, se quiseres.",
                style = MaterialTheme.typography.bodySmall,
                color = GeodouroTextSecondary
            )
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.take(280)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                placeholder = {
                    Text("Ex.: junto ao caminho, zona húmida, floração abundante...")
                },
                shape = RoundedCornerShape(12.dp),
                colors = geodouroOutlinedTextFieldColors()
            )
        }
    }
}

@Composable
private fun WikipediaLink(
    url: String,
    onOpen: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GeodouroLightBg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Wikipedia",
                style = MaterialTheme.typography.labelLarge,
                color = GeodouroTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = GeodouroBrandGreen,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onOpen)
            )
        }
    }
}
