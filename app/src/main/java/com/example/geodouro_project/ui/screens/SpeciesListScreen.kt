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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.geodouro_project.data.repository.PlantRepository
import com.example.geodouro_project.data.repository.PlantRepository.PlantSpeciesCatalogItem
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.ui.components.GeoFloraHeaderLogo
import com.example.geodouro_project.ui.theme.GeodouroBg
import com.example.geodouro_project.ui.theme.GeodouroGreen
import com.example.geodouro_project.ui.theme.GeodouroGrey
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
import com.example.geodouro_project.ui.theme.geodouroOutlinedTextFieldColors
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SpeciesFilter {
    FAMILY, GENUS, SPECIES
}

data class SpeciesListItem(
    val id: String,
    val scientificName: String,
    val commonName: String,
    val family: String,
    val genus: String,
    val imageCount: Int,
    val thumbnailUri: String?
)

data class SpeciesListUiState(
    val species: List<SpeciesListItem> = emptyList(),
    val isLoading: Boolean = true
)

class SpeciesListViewModel(
    private val repository: PlantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpeciesListUiState())
    val uiState: StateFlow<SpeciesListUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = SpeciesListUiState(isLoading = true)
            _uiState.value = SpeciesListUiState(
                species = repository.fetchSpeciesCatalogRemoteFirst().toRemoteSpeciesListItems(),
                isLoading = false
            )
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SpeciesListViewModel(
                        AppContainer.providePlantRepository(context)
                    ) as T
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun SpeciesListScreen(
    refreshTrigger: Int = 0,
    onSpeciesClick: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: SpeciesListViewModel = viewModel(
        factory = SpeciesListViewModel.factory(context.applicationContext)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = { viewModel.refresh() }
    )
    var selectedFilter by rememberSaveable { mutableStateOf(SpeciesFilter.SPECIES) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredSpecies = remember(uiState.species, searchQuery, selectedFilter) {
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        uiState.species.filter { species ->
            if (query.isBlank()) {
                true
            } else {
                species.scientificName.lowercase(Locale.ROOT).contains(query) ||
                    species.commonName.lowercase(Locale.ROOT).contains(query) ||
                    species.family.lowercase(Locale.ROOT).contains(query) ||
                    species.genus.lowercase(Locale.ROOT).contains(query)
            }
        }.sortedWith(
            when (selectedFilter) {
                SpeciesFilter.FAMILY -> compareBy<SpeciesListItem> { it.family }.thenBy { it.scientificName }
                SpeciesFilter.GENUS -> compareBy<SpeciesListItem> { it.genus }.thenBy { it.scientificName }
                SpeciesFilter.SPECIES -> compareBy<SpeciesListItem> { it.scientificName }
            }
        )
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            viewModel.refresh()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                expandedHeight = 48.dp,
                title = {
                    GeoFloraHeaderLogo()
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GeodouroBg
                )
            )
        },
        containerColor = GeodouroBg
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GeodouroBg)
                .pullRefresh(pullRefreshState)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = geodouroOutlinedTextFieldColors(),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = GeodouroGrey)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Limpar pesquisa",
                                    tint = GeodouroGrey
                                )
                            }
                        }
                    },
                    placeholder = {
                        Text("Pesquisar por espécie, nome comum, família...")
                    }
                )

                FilterTabRow(
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it }
                )

                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "A carregar espécies observadas...",
                                color = GeodouroTextSecondary
                            )
                        }
                    }

                    filteredSpecies.isEmpty() -> {
                        EmptySpeciesState(
                            hasQuery = searchQuery.isNotBlank(),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = filteredSpecies,
                                key = { it.id }
                            ) { species ->
                                SpeciesCard(
                                    species = species,
                                    onClick = { onSpeciesClick(species.id) }
                                )
                            }
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = uiState.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = GeodouroBg,
                contentColor = GeodouroGreen
            )
        }
    }
}

@Composable
fun FilterTabRow(
    selectedFilter: SpeciesFilter,
    onFilterSelected: (speciesFilter: SpeciesFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SpeciesFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        filter.name,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                enabled = true,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GeodouroGreen,
                    selectedLabelColor = Color.White,
                    containerColor = GeodouroLightBg,
                    labelColor = GeodouroTextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedFilter == filter,
                    borderColor = Color.Transparent,
                    selectedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun SpeciesCard(
    species: SpeciesListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnailRequest = remember(species.thumbnailUri) {
        ImageRequest.Builder(context)
            .data(species.thumbnailUri)
            .size(SPECIES_THUMBNAIL_MAX_SIZE)
            .crossfade(false)
            .build()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(GeodouroLightBg),
                contentAlignment = Alignment.BottomEnd
            ) {
                if (!species.thumbnailUri.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbnailRequest,
                        contentDescription = species.scientificName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = GeodouroGrey,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(topStart = 8.dp)
                ) {
                    Text(
                        text = species.imageCount.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        species.scientificName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GeodouroTextPrimary
                    )
                    Text(
                        species.commonName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GeodouroTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeciesMetaChip(species.family)
                    SpeciesMetaChip(species.genus)
                }
            }
        }
    }
}

@Composable
fun SpeciesMetaChip(label: String) {
    Surface(
        color = GeodouroLightBg,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = GeodouroTextSecondary
        )
    }
}

@Composable
private fun EmptySpeciesState(
    hasQuery: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (hasQuery) {
                    "Nenhuma espécie encontrada para essa pesquisa."
                } else {
                    "Ainda não existem espécies guardadas."
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GeodouroTextPrimary
            )
            Text(
                text = if (hasQuery) {
                    "Tenta outro nome cientifico, comum, familia ou genero."
                } else {
                    "Assim que confirmares identificações, elas vão aparecer aqui."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = GeodouroTextSecondary
            )
        }
    }
}

private fun List<PlantSpeciesCatalogItem>.toRemoteSpeciesListItems(): List<SpeciesListItem> {
    return map { species ->
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

fun String.toSpeciesId(): String {
    return trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), "_")
}

private const val SPECIES_THUMBNAIL_MAX_SIZE = 240
