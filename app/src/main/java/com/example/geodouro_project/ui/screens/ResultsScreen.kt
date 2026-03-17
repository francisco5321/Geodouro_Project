package com.example.geodouro_project.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onBackClick: () -> Unit,
    onConfirmResult: (IdentificationResult) -> Unit,
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
    var shouldNavigateAfterSave by remember { mutableStateOf(false) }

    LaunchedEffect(localInferenceResult) {
        viewModel.loadHybridResult(localInferenceResult)
    }

    LaunchedEffect(uiState, shouldNavigateAfterSave) {
        if (!shouldNavigateAfterSave) {
            return@LaunchedEffect
        }

        val currentState = uiState as? ResultsUiState.Success ?: return@LaunchedEffect
        if (currentState.saveMessage == null) {
            return@LaunchedEffect
        }

        onConfirmResult(currentState.result.toIdentificationResult(currentState.sourceLabel))
        shouldNavigateAfterSave = false
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
                        onSyncPending = { viewModel.syncPendingObservations() },
                        onConfirm = {
                            shouldNavigateAfterSave = true
                            viewModel.confirmObservation()
                        }
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
    onSyncPending: () -> Unit,
    onConfirm: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ResultPhotosSection(
                capturedImageUri = result.capturedImageUri,
                referencePhotoUrl = result.photoUrl
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
                Text(
                    text = "Wikipedia: ${result.wikipediaUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroTextSecondary
                )
            }

            if (result.alternativePredictions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                AlternativePredictionsSection(
                    predictions = result.alternativePredictions
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
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GeodouroGreen
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Confirmar e guardar", color = Color.White)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onSyncPending,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Tentar sincronizar pendentes")
            }
        }
    }
}

@Composable
private fun ResultPhotosSection(
    capturedImageUri: String,
    referencePhotoUrl: String?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ResultPhotoCard(
            title = "Foto capturada",
            imageModel = capturedImageUri,
            emptyMessage = "Sem foto capturada."
        )

        ResultPhotoCard(
            title = "Foto de referencia",
            imageModel = referencePhotoUrl,
            emptyMessage = "Sem foto remota disponivel para esta especie."
        )
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
