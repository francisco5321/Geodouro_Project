package com.example.geodouro_project.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.geodouro_project.R
import com.example.geodouro_project.domain.model.LocalInferenceResult
import com.example.geodouro_project.domain.model.LocalPredictionCandidate
import com.example.geodouro_project.ui.theme.GeodouroBrandGreen
import com.example.geodouro_project.ui.theme.GeodouroGreen
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
import com.example.geodouro_project.ui.theme.geodouroOutlinedBorderColor
import com.example.geodouro_project.ui.theme.geodouroOutlinedButtonColors
import com.example.geodouro_project.ui.theme.geodouroPrimaryButtonColors
import com.example.geodouro_project.ui.theme.geodouroSecondaryButtonColors

data class IdentificationResult(
    val scientificName: String,
    val commonName: String,
    val family: String,
    val confidence: Float,
    val sourceLabel: String,
    val wikipediaUrl: String?,
    val photoUrl: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    refreshTrigger: Int = 0,
    onBackClick: () -> Unit,
    onConfirmResult: (IdentificationResult) -> Unit,
    multiImageUris: List<String> = emptyList(),
    captureLatitude: Double? = null,
    captureLongitude: Double? = null,
    localInferenceResult: LocalInferenceResult = LocalInferenceResult(
        imageUri = "",
        latitude = null,
        longitude = null,
        predictedSpecies = "Sem inferencia local",
        confidence = 0f
    )
) {
    val context = LocalContext.current
    val viewModel: ResultsViewModel = viewModel(
        factory = ResultsViewModel.factory(context.applicationContext)
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(localInferenceResult, multiImageUris, captureLatitude, captureLongitude) {
        if (multiImageUris.size >= 2) {
            viewModel.loadMultiImageResult(
                imageUris = multiImageUris,
                latitude = captureLatitude,
                longitude = captureLongitude
            )
        } else {
            viewModel.loadHybridResult(localInferenceResult)
        }
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger <= 0) {
            return@LaunchedEffect
        }

        if (multiImageUris.size >= 2) {
            viewModel.loadMultiImageResult(
                imageUris = multiImageUris,
                latitude = captureLatitude,
                longitude = captureLongitude
            )
        } else {
            viewModel.loadHybridResult(localInferenceResult)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Identificacao - Resultados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GeodouroBrandGreen
                        )
                        Image(
                            painter = painterResource(id = R.drawable.logo_s_fundo),
                            contentDescription = "Geodouro",
                            modifier = Modifier.height(28.dp)
                        )
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
                    containerColor = GeodouroWhite
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GeodouroWhite)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (val state = uiState) {
                is ResultsUiState.Idle,
                is ResultsUiState.Loading -> {
                    HybridLoadingCard()
                }

                is ResultsUiState.Error -> {
                    ErrorCard(message = state.message)
                }

                is ResultsUiState.Success -> {
                    ResultCard(
                        result = state.result,
                        sourceLabel = state.sourceLabel,
                        saveMessage = state.saveMessage,
                        onConfirm = { viewModel.confirmObservation() },
                        onRetakePhotos = onBackClick
                    )
                }

                is ResultsUiState.MultiImageSuccess -> {
                    MultiImageResultCard(
                        result = state.result,
                        sourceLabel = state.sourceLabel,
                        saveMessage = state.saveMessage,
                        onConfirm = { viewModel.confirmObservation() },
                        onRetakePhotos = onBackClick
                    )
                }
            }
        }
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
    onConfirm: () -> Unit,
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
                AlternativePredictionsSection(
                    predictions = result.alternativePredictions
                )
            }

            CoordinatesSection(
                latitude = result.latitude,
                longitude = result.longitude
            )

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
                onClick = if (result.isPlantDetected) onConfirm else onRetakePhotos,
                modifier = Modifier.fillMaxWidth(),
                colors = geodouroPrimaryButtonColors(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (result.isPlantDetected) "Confirmar e guardar" else "Tirar novas fotos"
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
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ResultPhotoCard(
            title = "Foto capturada",
            imageModel = capturedImageUri,
            emptyMessage = "Sem foto capturada."
        )

        if (isPlantDetected) {
            ResultPhotoCard(
                title = "Foto de referencia",
                imageModel = referencePhotoUrl,
                emptyMessage = "Sem foto remota disponivel para esta especie."
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
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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
                model = imageModel,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GeodouroLightBg),
                contentScale = ContentScale.Crop
            )
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
                "Coordenadas: localizacao indisponivel"
            },
            modifier = Modifier.padding(10.dp),
            color = GeodouroTextPrimary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AlternativePredictionsSection(predictions: List<LocalPredictionCandidate>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
fun MultiImageResultCard(
    result: MultiImageResultUiModel,
    sourceLabel: String,
    saveMessage: String?,
    onConfirm: () -> Unit,
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
            // Mostrar consenso e número de imagens
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = GeodouroLightBg,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Análise de ${result.imagesCount} imagem(ns)",
                            style = MaterialTheme.typography.bodySmall,
                            color = GeodouroTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Consenso: ${(result.consensus * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = GeodouroTextSecondary
                        )
                    }
                    Text(
                        text = "${result.processingTimeMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = GeodouroTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            MultiImagePhotosSection(
                capturedImageUris = result.imageUris,
                referencePhotoUrl = result.photoUrl,
                referenceTitle = "Foto da planta mais parecida",
                isPlantDetected = result.isPlantDetected
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = result.finalSpecies,
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
                    text = "${(result.aggregatedConfidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = GeodouroGreen,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { result.aggregatedConfidence },
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

            // Mostrar alternativa se existir
            if (!result.topAlternative.isNullOrBlank() && result.topAlternativeConfidence != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Alternativa:",
                                style = MaterialTheme.typography.labelSmall,
                                color = GeodouroTextSecondary
                            )
                            Text(
                                text = result.topAlternative ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeodouroTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${(result.topAlternativeConfidence!! * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = GeodouroTextPrimary
                        )
                    }
                }
            }

            CoordinatesSection(
                latitude = result.latitude,
                longitude = result.longitude
            )

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
                onClick = if (result.isPlantDetected) onConfirm else onRetakePhotos,
                modifier = Modifier.fillMaxWidth(),
                colors = geodouroPrimaryButtonColors(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (result.isPlantDetected) "Confirmar e guardar" else "Tirar novas fotos"
                )
            }
        }
    }
}

@Composable
private fun MultiImagePhotosSection(
    capturedImageUris: List<String>,
    referencePhotoUrl: String?,
    referenceTitle: String,
    isPlantDetected: Boolean
) {
    var selectedImageIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Fotos capturadas",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = GeodouroTextPrimary
        )

        if (capturedImageUris.isEmpty()) {
            ResultPhotoCard(
                title = "Capturas",
                imageModel = null,
                emptyMessage = "Sem fotos capturadas para mostrar."
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                capturedImageUris.forEachIndexed { index, imageUri ->
                    Box {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Foto capturada ${index + 1}",
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GeodouroLightBg)
                                .clickable { selectedImageIndex = index },
                            contentScale = ContentScale.Crop
                        )

                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = GeodouroWhite.copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = "Foto ${index + 1}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = GeodouroTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (isPlantDetected) {
            ResultPhotoCard(
                title = referenceTitle,
                imageModel = referencePhotoUrl,
                emptyMessage = "Sem foto remota disponivel para esta especie."
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

    if (selectedImageIndex != null && capturedImageUris.isNotEmpty()) {
        val currentIndex = selectedImageIndex!!.coerceIn(0, capturedImageUris.lastIndex)
        val currentImageUri = capturedImageUris[currentIndex]

        Dialog(
            onDismissRequest = { selectedImageIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    AsyncImage(
                        model = currentImageUri,
                        contentDescription = "Foto capturada ampliada",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .pointerInput(currentIndex, capturedImageUris.size) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { _, dragAmount ->
                                        when {
                                            dragAmount > 20f && currentIndex > 0 -> {
                                                selectedImageIndex = currentIndex - 1
                                            }
                                            dragAmount < -20f && currentIndex < capturedImageUris.lastIndex -> {
                                                selectedImageIndex = currentIndex + 1
                                            }
                                        }
                                    }
                                )
                            },
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { selectedImageIndex = (currentIndex - 1).coerceAtLeast(0) },
                            enabled = currentIndex > 0,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                geodouroOutlinedBorderColor(currentIndex > 0)
                            ),
                            colors = geodouroOutlinedButtonColors()
                        ) {
                            Text("Anterior")
                        }

                        Text(
                            text = "${currentIndex + 1} / ${capturedImageUris.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        OutlinedButton(
                            onClick = { selectedImageIndex = (currentIndex + 1).coerceAtMost(capturedImageUris.lastIndex) },
                            enabled = currentIndex < capturedImageUris.lastIndex,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                geodouroOutlinedBorderColor(currentIndex < capturedImageUris.lastIndex)
                            ),
                            colors = geodouroOutlinedButtonColors()
                        ) {
                            Text("Seguinte")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { selectedImageIndex = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = geodouroSecondaryButtonColors()
                    ) {
                        Text("Fechar")
                    }
                }
            }
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

private fun MultiImageResultUiModel.toIdentificationResult(sourceLabel: String): IdentificationResult {
    return IdentificationResult(
        scientificName = finalSpecies,
        commonName = commonName,
        family = family,
        confidence = aggregatedConfidence,
        sourceLabel = sourceLabel,
        wikipediaUrl = wikipediaUrl,
        photoUrl = photoUrl
    )
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
















