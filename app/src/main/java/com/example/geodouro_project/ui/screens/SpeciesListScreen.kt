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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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

data class SpeciesListItem(
    val id: String,
    val scientificName: String,
    val commonName: String,
    val family: String,
    val genus: String,
    val imageCount: Int,
    val thumbnailUri: String?
)

data class SpeciesFamilyListItem(
    val family: String,
    val speciesCount: Int,
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
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFamily by rememberSaveable { mutableStateOf<String?>(null) }

    val baseFilteredSpecies = remember(uiState.species, searchQuery) {
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
        }
    }

    val filteredFamilies = remember(baseFilteredSpecies, selectedFamily) {
        if (selectedFamily != null) {
            emptyList()
        } else {
            baseFilteredSpecies
                .groupBy { it.family }
                .map { (family, species) ->
                    SpeciesFamilyListItem(
                        family = family,
                        speciesCount = species.size,
                        imageCount = species.sumOf { it.imageCount },
                        thumbnailUri = species.firstOrNull { !it.thumbnailUri.isNullOrBlank() }?.thumbnailUri
                    )
                }
                .sortedBy { it.family }
        }
    }

    val filteredSpecies = remember(baseFilteredSpecies, selectedFamily) {
        baseFilteredSpecies
            .let { species ->
                if (selectedFamily == null) species else species.filter { it.family == selectedFamily }
            }
            .sortedBy { it.scientificName }
    }

    LaunchedEffect(uiState.species, selectedFamily) {
        if (selectedFamily != null && uiState.species.none { it.family == selectedFamily }) {
            selectedFamily = null
        }
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
                        Text("Pesquisar por especie, nome comum, familia...")
                    }
                )

                if (selectedFamily != null) {
                    SelectedFamilyBanner(
                        family = selectedFamily.orEmpty(),
                        onClear = { selectedFamily = null }
                    )
                }

                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "A carregar especies observadas...",
                                color = GeodouroTextSecondary
                            )
                        }
                    }

                    selectedFamily == null && filteredFamilies.isEmpty() -> {
                        EmptySpeciesState(
                            hasQuery = searchQuery.isNotBlank(),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    selectedFamily != null && filteredSpecies.isEmpty() -> {
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
                            if (selectedFamily == null) {
                                items(
                                    items = filteredFamilies,
                                    key = { it.family }
                                ) { family ->
                                    FamilyCard(
                                        family = family,
                                        onClick = { selectedFamily = family.family }
                                    )
                                }
                            } else {
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
private fun SelectedFamilyBanner(
    family: String,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = family,
            style = MaterialTheme.typography.titleSmall,
            color = GeodouroTextPrimary,
            fontWeight = FontWeight.Bold
        )
        Surface(
            modifier = Modifier.clickable(onClick = onClear),
            color = GeodouroGreen,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Ver familias",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = GeodouroWhite,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FamilyCard(
    family: SpeciesFamilyListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnailRequest = remember(family.thumbnailUri) {
        ImageRequest.Builder(context)
            .data(family.thumbnailUri)
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
                if (!family.thumbnailUri.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbnailRequest,
                        contentDescription = family.family,
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
                        text = family.imageCount.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = family.family,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GeodouroTextPrimary
                )
                Text(
                    text = "${family.speciesCount} especies nesta familia",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GeodouroTextSecondary
                )
                SpeciesMetaChip("Abrir especies")
            }
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
                    "Nenhuma especie encontrada para essa pesquisa."
                } else {
                    "Ainda nao existem especies guardadas."
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GeodouroTextPrimary
            )
            Text(
                text = if (hasQuery) {
                    "Tenta outro nome cientifico, comum, familia ou genero."
                } else {
                    "Assim que confirmares identificacoes, elas vao aparecer aqui."
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
