package com.example.geodouro_project.ui.screens

import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.example.geodouro_project.data.repository.PlantRepository
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.domain.model.ObservationSyncStatus
import com.example.geodouro_project.ui.components.GeoFloraHeaderLogo
import com.example.geodouro_project.ui.theme.GeodouroBg
import com.example.geodouro_project.ui.theme.GeodouroBrandGreen
import com.example.geodouro_project.ui.theme.GeodouroDarkGreen
import com.example.geodouro_project.ui.theme.GeodouroGreen
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroLightGreen
import com.example.geodouro_project.ui.theme.GeodouroMint
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
    val id: String,
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
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.value = SpeciesDetailUiState(isLoading = true)
            _uiState.value = SpeciesDetailUiState(
                detail = repository.fetchSpeciesDetailRemoteFirst(speciesId)?.toUiModel(),
                isLoading = false
            )
        }
    }

    companion object {
        fun factory(context: Context, speciesId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SpeciesDetailViewModel(
                        repository = AppContainer.providePlantRepository(context),
                        speciesId = speciesId
                    ) as T
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
    val decodedId = remember(speciesId) { Uri.decode(speciesId) }
    var fullscreenImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    val viewModel: SpeciesDetailViewModel = viewModel(
        factory = SpeciesDetailViewModel.factory(context.applicationContext, decodedId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                expandedHeight = 48.dp,
                title = {
                    GeoFloraHeaderLogo()
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = GeodouroBrandGreen
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
        when {
            uiState.isLoading -> CenteredMessage(padding, "A carregar detalhe da especie...")
            uiState.detail == null -> CenteredMessage(padding, "Nao foi possivel encontrar esta especie.")
            else -> {
                val detail = uiState.detail ?: return@Scaffold
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        HeroImage(
                            detail = detail,
                            onImageClick = { imageUri -> fullscreenImageUri = imageUri }
                        )
                    }
                    item {
                        IdentityCard(
                            detail = detail,
                            onImageClick = { imageUri -> fullscreenImageUri = imageUri }
                        )
                    }
                    item { LocationCard(detail.locationSummary) }

                    if (!detail.wikipediaUrl.isNullOrBlank()) {
                        item {
                            OutlinedButton(
                                onClick = { uriHandler.openUri(detail.wikipediaUrl) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = GeodouroBrandGreen
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Abrir Wikipedia", fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    if (detail.observations.isNotEmpty()) {
                        item {
                            Text(
                                text = "OBSERVACOES RECENTES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = GeodouroTextSecondary,
                                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                            )
                        }
                        items(detail.observations) { obs ->
                            ObservationPreviewCard(
                                observation = obs,
                                onClick = { onObservationClick(obs.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    fullscreenImageUri?.let { imageUri ->
        FullscreenImageDialog(
            imageUri = imageUri,
            contentDescription = uiState.detail?.scientificName ?: "Imagem da especie",
            onDismiss = { fullscreenImageUri = null }
        )
    }
}

@Composable
private fun CenteredMessage(padding: PaddingValues, text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(GeodouroBg),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = GeodouroTextSecondary)
    }
}

@Composable
private fun HeroImage(
    detail: SpeciesDetail,
    onImageClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GeodouroLightBg),
        contentAlignment = Alignment.Center
    ) {
        if (!detail.heroImageUri.isNullOrBlank()) {
            AsyncImage(
                model = detail.heroImageUri,
                contentDescription = detail.scientificName,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onImageClick(detail.heroImageUri) },
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0x55000000))
                        )
                    )
            )
        } else {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = GeodouroGreen,
                modifier = Modifier.size(56.dp)
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = GeodouroWhite.copy(alpha = 0.92f),
            tonalElevation = 0.dp
        ) {
            Text(
                text = "${detail.imageCount} imagens",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = GeodouroDarkGreen
            )
        }
    }
}

@Composable
private fun IdentityCard(
    detail: SpeciesDetail,
    onImageClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = detail.scientificName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = GeodouroTextPrimary
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "${detail.commonName} · ${detail.family}",
                style = MaterialTheme.typography.bodyMedium,
                color = GeodouroTextSecondary
            )

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaxonChip(detail.family)
                TaxonChip(detail.genus)
            }

            if (detail.galleryImageUris.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(detail.galleryImageUris) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = detail.scientificName,
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GeodouroLightBg)
                                .clickable { onImageClick(uri) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCell(Modifier.weight(1f), detail.observationCount.toString(), "Observ.")
                StatCell(Modifier.weight(1f), detail.imageCount.toString(), "Imagens")
                StatCell(Modifier.weight(1f), detail.syncedCount.toString(), "Sincron.")
                StatCell(Modifier.weight(1f), detail.publishedCount.toString(), "Publicas")
            }
        }
    }
}

@Composable
private fun TaxonChip(label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = GeodouroLightGreen
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = GeodouroDarkGreen
        )
    }
}

@Composable
private fun StatCell(modifier: Modifier, value: String, label: String) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = GeodouroMint
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                color = GeodouroBrandGreen
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = GeodouroDarkGreen,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FullscreenImageDialog(
    imageUri: String,
    contentDescription: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fechar imagem",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun LocationCard(summary: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp),
                color = GeodouroLightGreen
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = GeodouroBrandGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LOCALIZACAO",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = GeodouroTextSecondary
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GeodouroTextPrimary,
                    lineHeight = 21.sp
                )
            }
        }
    }
}

@Composable
private fun ObservationPreviewCard(
    observation: ObservationEntity,
    onClick: () -> Unit
) {
    val dateFormatter = rememberDateFormatter()
    val status = buildObservationStatusLabel(observation)
    val isPublished = observation.isPublished

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(GeodouroLightBg),
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
                        tint = GeodouroGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = observation.enrichedCommonName?.takeIf { it.isNotBlank() }
                        ?: observation.predictedSpecies,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = GeodouroTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormatter.format(Date(observation.capturedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroTextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConfidenceBadge("${(observation.confidence * 100).toInt()}%")
                    StatusBadge(status, isPublished)
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = GeodouroTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ConfidenceBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = GeodouroLightBg
    ) {
        Text(
            text = "Conf. $text",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = GeodouroTextSecondary
        )
    }
}

@Composable
private fun StatusBadge(status: String, isPublished: Boolean) {
    val bgColor = if (isPublished) GeodouroLightGreen else GeodouroLightBg
    val textColor = if (isPublished) GeodouroDarkGreen else GeodouroTextSecondary

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isPublished) FontWeight.Medium else FontWeight.Normal,
            color = textColor
        )
    }
}

@Composable
private fun rememberDateFormatter(): SimpleDateFormat =
    SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())

private fun PlantRepository.PlantSpeciesDetailData.toUiModel(): SpeciesDetail =
    SpeciesDetail(
        id = id,
        scientificName = scientificName,
        commonName = commonName?.takeIf { it.isNotBlank() } ?: "Sem nome comum",
        family = family.ifBlank { "Familia desconhecida" },
        genus = genus.ifBlank { "Genero desconhecido" },
        imageCount = imageCount,
        observationCount = observationCount,
        wikipediaUrl = wikipediaUrl,
        heroImageUri = heroImageUri,
        galleryImageUris = galleryImageUris,
        locationSummary = locationSummary,
        syncedCount = syncedCount,
        publishedCount = publishedCount,
        observations = observations
    )

private fun buildObservationStatusLabel(observation: ObservationEntity): String =
    when {
        observation.isPublished -> "Publicada"
        observation.syncStatus == ObservationSyncStatus.SYNCED.name -> "Sincronizada"
        observation.syncStatus == ObservationSyncStatus.FAILED.name -> "Falha de sincronizacao"
        else -> "Pendente"
    }
