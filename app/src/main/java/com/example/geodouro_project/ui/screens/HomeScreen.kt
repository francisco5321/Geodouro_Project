package com.example.geodouro_project.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geodouro_project.R
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.example.geodouro_project.data.repository.PlantRepository
import com.example.geodouro_project.data.repository.PlantRepository.PlantSpeciesCatalogItem
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.ui.theme.GeodouroBrandGreen
import com.example.geodouro_project.ui.theme.GeodouroGrey
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val speciesCount: Int = 0,
    val observationsCount: Int = 0,
    val recentSpecies: List<SpeciesListItem> = emptyList()
)

class HomeViewModel(
    private val repository: PlantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val speciesCatalog = repository.fetchSpeciesCatalogRemoteFirst()
            val stats = repository.fetchObservationStatsRemoteFirst()
            _uiState.value = HomeUiState(
                speciesCount = stats.speciesCount,
                observationsCount = stats.observationsCount,
                recentSpecies = speciesCatalog.toRemoteRecentSpeciesItems()
            )
        }
    }

    companion object {
        fun factory(context: android.content.Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(
                        AppContainer.providePlantRepository(context)
                    ) as T
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSpeciesClick: (String) -> Unit = {},
    onOpenSpeciesList: () -> Unit = {},
    onOpenRoutePlans: () -> Unit = {},
    onOpenVisitTargets: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(context.applicationContext)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.logo_s_fundo),
                        contentDescription = "Geodouro",
                        modifier = Modifier.height(80.dp)
                    )
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GeodouroLightBg),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onOpenSpeciesList) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Pesquisar",
                                tint = GeodouroBrandGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GeodouroWhite
                )
            )
        },
        containerColor = GeodouroWhite
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GeodouroWhite),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    GeodouroBrandGreen,
                                    GeodouroBrandGreen.copy(alpha = 0.75f)
                                )
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                ) {
                    Column {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.padding(bottom = 14.dp)
                        ) {
                            Text(
                                text = "Flora Portuguesa",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                        Text(
                            text = "Identificacao\nde Flora",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            lineHeight = 32.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Capture e identifique especies da flora portuguesa com inteligencia artificial.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        icon = Icons.Default.Eco,
                        value = uiState.speciesCount.toString(),
                        label = "Especies\nencontradas",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Default.Check,
                        value = uiState.observationsCount.toString(),
                        label = "Observacoes\nrealizadas",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                        .clickable(onClick = onOpenRoutePlans),
                    shape = RoundedCornerShape(18.dp),
                    color = GeodouroLightBg
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(GeodouroBrandGreen.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                tint = GeodouroBrandGreen
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Percursos Planeados",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GeodouroTextPrimary
                            )
                            Text(
                                text = "Abre os percursos criados na web e consulta a ordem das paragens.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeodouroTextSecondary
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 4.dp)
                        .clickable(onClick = onOpenVisitTargets),
                    shape = RoundedCornerShape(18.dp),
                    color = GeodouroLightBg
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(GeodouroBrandGreen.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = GeodouroBrandGreen
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Quero visitar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GeodouroTextPrimary
                            )
                            Text(
                                text = "Consulta os alvos guardados na web e remove pontos que ja nao queres visitar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeodouroTextSecondary
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 10.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(GeodouroBrandGreen)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Especies Recentes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GeodouroTextPrimary
                    )
                }
            }

            if (uiState.recentSpecies.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = GeodouroLightBg
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(GeodouroBrandGreen.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Eco,
                                    contentDescription = null,
                                    tint = GeodouroBrandGreen,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Ainda sem especies",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = GeodouroTextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "As especies recentes vao aparecer aqui depois das primeiras identificacoes confirmadas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeodouroTextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            } else {
                items(uiState.recentSpecies) { species ->
                    SpeciesCard(
                        species = species,
                        onClick = { onSpeciesClick(species.id) },
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = GeodouroLightBg,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GeodouroBrandGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = GeodouroBrandGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = GeodouroTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = GeodouroTextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

private fun List<ObservationEntity>.toRecentSpeciesItems(): List<SpeciesListItem> {
    return groupBy { observation ->
        observation.enrichedScientificName
            ?.takeIf { it.isNotBlank() }
            ?: observation.predictedSpecies
    }.mapNotNull { (scientificName, observations) ->
        val newestObservation = observations.maxByOrNull { it.capturedAt } ?: return@mapNotNull null
        SpeciesListItem(
            id = scientificName.toSpeciesId(),
            scientificName = scientificName,
            commonName = newestObservation.enrichedCommonName?.takeIf { it.isNotBlank() } ?: "Sem nome comum",
            family = newestObservation.enrichedFamily?.takeIf { it.isNotBlank() } ?: "Familia desconhecida",
            genus = scientificName.substringBefore(" ").ifBlank { "Genero desconhecido" },
            imageCount = observations.sumOf { it.allImageUris().size },
            thumbnailUri = newestObservation.allImageUris().firstOrNull()
        ) to newestObservation.capturedAt
    }.sortedByDescending { it.second }
        .take(3)
        .map { it.first }
}

private fun List<PlantSpeciesCatalogItem>.toRemoteRecentSpeciesItems(): List<SpeciesListItem> {
    return sortedByDescending { it.updatedAtEpochMs }
        .take(3)
        .map { species ->
            SpeciesListItem(
                id = species.id,
                scientificName = species.scientificName,
                commonName = species.commonName?.takeIf { it.isNotBlank() } ?: "Sem nome comum",
                family = species.family.ifBlank { "Familia desconhecida" },
                genus = species.genus.ifBlank { "Genero desconhecido" },
                imageCount = species.imageCount,
                thumbnailUri = species.thumbnailUri
            )
        }
}


