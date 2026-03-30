package com.example.geodouro_project.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.geodouro_project.R
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.example.geodouro_project.data.repository.PlantRepository
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.domain.model.ObservationSyncStatus
import com.example.geodouro_project.ui.theme.GeodouroBrandGreen
import com.example.geodouro_project.ui.theme.GeodouroGreen
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SpeciesDetailUiState(
    val detail: SpeciesDetail? = null,
    val isLoading: Boolean = true
)

data class SpeciesDetail(
    val scientificName: String,
    val commonName: String,
    val family: String,
    val genus: String,
    val imageCount: Int,
    val observationCount: Int,
    val wikipediaUrl: String?,
    val heroImageUri: String?,
    val galleryImageUris: List<String>,
    val locationSummary: String,
    val syncedCount: Int,
    val publishedCount: Int,
    val observations: List<ObservationEntity>
)

class SpeciesDetailViewModel(
    private val repository: PlantRepository,
    private val speciesId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpeciesDetailUiState())
    val uiState: StateFlow<SpeciesDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeObservations().collect { observations ->
                val matchingObservations = observations.filter { observation ->
                    val scientificName = observation.enrichedScientificName
                        ?.takeIf { it.isNotBlank() }
                        ?: observation.predictedSpecies
                    scientificName.toSpeciesId() == speciesId
                }

                _uiState.value = SpeciesDetailUiState(
                    detail = matchingObservations.toSpeciesDetail(),
                    isLoading = false
                )
            }
        }
    }

    companion object {
        fun factory(context: Context, speciesId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SpeciesDetailViewModel(
                        repository = AppContainer.providePlantRepository(context),
                        speciesId = speciesId
                    ) as T
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesDetailScreen(
    speciesId: String,
    onBackClick: () -> Unit,
    onObservationClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val viewModel: SpeciesDetailViewModel = viewModel(
        factory = SpeciesDetailViewModel.factory(context.applicationContext, speciesId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.logo_s_fundo),
                        contentDescription = "Geodouro",
                        modifier = Modifier.height(56.dp)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = GeodouroBrandGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GeodouroWhite
                )
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(GeodouroWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Text("A carregar detalhe da especie...", color = GeodouroTextSecondary)
                }
            }

            uiState.detail == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(GeodouroWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nao foi possivel encontrar esta especie.", color = GeodouroTextSecondary)
                }
            }

            else -> {
                val detail = uiState.detail ?: return@Scaffold
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(GeodouroWhite),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
                            elevation = CardDefaults.cardElevation(2.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .background(GeodouroLightBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!detail.heroImageUri.isNullOrBlank()) {
                                        AsyncImage(
                                            model = detail.heroImageUri,
                                            contentDescription = detail.scientificName,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = GeodouroGreen,
                                            modifier = Modifier.size(56.dp)
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = detail.scientificName,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = GeodouroTextPrimary
                                    )
                                    Text(
                                        text = detail.commonName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = GeodouroTextSecondary
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SpeciesMetaChip(detail.family)
                                        SpeciesMetaChip(detail.genus)
                                    }

                                    if (detail.galleryImageUris.isNotEmpty()) {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(detail.galleryImageUris) { imageUri ->
                                                AsyncImage(
                                                    model = imageUri,
                                                    contentDescription = detail.scientificName,
                                                    modifier = Modifier
                                                        .size(96.dp)
                                                        .background(GeodouroLightBg, RoundedCornerShape(10.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        DetailStatCard(
                                            modifier = Modifier.weight(1f),
                                            value = detail.observationCount.toString(),
                                            label = "Observacoes"
                                        )
                                        DetailStatCard(
                                            modifier = Modifier.weight(1f),
                                            value = detail.imageCount.toString(),
                                            label = "Imagens"
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        DetailStatCard(
                                            modifier = Modifier.weight(1f),
                                            value = detail.syncedCount.toString(),
                                            label = "Sincronizadas"
                                        )
                                        DetailStatCard(
                                            modifier = Modifier.weight(1f),
                                            value = detail.publishedCount.toString(),
                                            label = "Publicadas"
                                        )
                                    }

                                    Surface(
                                        color = GeodouroLightBg,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = detail.locationSummary,
                                            modifier = Modifier.padding(12.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = GeodouroTextPrimary
                                        )
                                    }

                                    if (!detail.wikipediaUrl.isNullOrBlank()) {
                                        Button(
                                            onClick = { uriHandler.openUri(detail.wikipediaUrl) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Link,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.size(8.dp))
                                            Text("Abrir Wikipedia")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Observacoes recentes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GeodouroTextPrimary
                        )
                    }

                    items(detail.observations) { observation ->
                        ObservationPreviewCard(
                            observation = observation,
                            onClick = { onObservationClick(observation.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = GeodouroLightBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = GeodouroBrandGreen
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = GeodouroTextSecondary
            )
        }
    }
}

@Composable
private fun ObservationPreviewCard(
    observation: ObservationEntity,
    onClick: () -> Unit
) {
    val dateFormatter = rememberDateFormatter()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(GeodouroLightBg, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                val imageUri = observation.allImageUris().firstOrNull()
                if (!imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = observation.predictedSpecies,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = GeodouroGreen
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = observation.enrichedCommonName?.takeIf { it.isNotBlank() }
                        ?: observation.predictedSpecies,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = GeodouroTextPrimary
                )
                Text(
                    text = dateFormatter.format(Date(observation.capturedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroTextSecondary
                )
                Text(
                    text = "Confianca ${(observation.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroTextSecondary
                )
                Text(
                    text = buildObservationStatusLabel(observation),
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroBrandGreen
                )
            }
        }
    }
}

@Composable
private fun rememberDateFormatter(): SimpleDateFormat {
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
}

private fun List<ObservationEntity>.toSpeciesDetail(): SpeciesDetail? {
    val first = firstOrNull() ?: return null
    val scientificName = first.enrichedScientificName?.takeIf { it.isNotBlank() }
        ?: first.predictedSpecies
    return SpeciesDetail(
        scientificName = scientificName,
        commonName = first.enrichedCommonName?.takeIf { it.isNotBlank() } ?: "Sem nome comum",
        family = first.enrichedFamily?.takeIf { it.isNotBlank() } ?: "Familia desconhecida",
        genus = scientificName.substringBefore(" ").ifBlank { "Genero desconhecido" },
        imageCount = sumOf { it.allImageUris().size },
        observationCount = size,
        wikipediaUrl = first.enrichedWikipediaUrl,
        heroImageUri = first.allImageUris().firstOrNull() ?: first.enrichedPhotoUrl,
        galleryImageUris = flatMap { it.allImageUris() }.distinct().take(8),
        locationSummary = buildSpeciesLocationSummary(this),
        syncedCount = count { it.syncStatus == ObservationSyncStatus.SYNCED.name },
        publishedCount = count { it.isPublished },
        observations = this
    )
}

private fun buildSpeciesLocationSummary(observations: List<ObservationEntity>): String {
    val withLocation = observations.filter { it.latitude != null && it.longitude != null }
    if (withLocation.isEmpty()) {
        return "Sem localizacoes registadas para esta especie."
    }

    val first = withLocation.first()
    val last = withLocation.last()
    return if (withLocation.size == 1) {
        "1 observacao com localizacao registada em GPS %.5f, %.5f".format(
            first.latitude,
            first.longitude
        )
    } else {
        "Localizacoes registadas em ${withLocation.size} observacoes. Intervalo aproximado: %.5f, %.5f ate %.5f, %.5f".format(
            first.latitude,
            first.longitude,
            last.latitude,
            last.longitude
        )
    }
}

private fun buildObservationStatusLabel(observation: ObservationEntity): String {
    return when {
        observation.isPublished -> "Publicada"
        observation.syncStatus == ObservationSyncStatus.SYNCED.name -> "Sincronizada"
        observation.syncStatus == ObservationSyncStatus.FAILED.name -> "Falha de sincronizacao"
        else -> "Pendente de sincronizacao"
    }
}
