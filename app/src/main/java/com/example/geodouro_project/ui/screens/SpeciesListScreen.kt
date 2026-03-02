package com.example.geodouro_project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.geodouro_project.ui.theme.*

enum class SpeciesFilter {
    FAMILY, GENUS, SPECIES
}

data class Species(
    val scientificName: String,
    val commonName: String,
    val family: String,
    val genus: String,
    val imageCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesListScreen() {
    var selectedFilter by remember { mutableStateOf(SpeciesFilter.SPECIES) }
    
    val speciesList = remember {
        listOf(
            Species("Thymus mastichina", "Tomilho", "Lamiaceae", "Thymus", 10),
            Species("Cistus ladanifer", "Esteva", "Cistaceae", "Cistus", 164),
            Species("Lavandula stoechas", "Rosmaninho", "Lamiaceae", "Lavandula", 89),
            Species("Arbutus unedo", "Medronheiro", "Ericaceae", "Arbutus", 45),
            Species("Quercus suber", "Sobreiro", "Fagaceae", "Quercus", 124)
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "GEODOURO Flora",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = GeodouroTextPrimary
                    )
                },
                actions = {
                    IconButton(onClick = { /* Search */ }) {
                        Icon(Icons.Default.Search, "Pesquisar", tint = GeodouroGrey)
                    }
                    IconButton(onClick = { /* Filter */ }) {
                        Icon(Icons.Default.FilterList, "Filtrar", tint = GeodouroGrey)
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
        ) {
            // Filtros horizontais (Family, Genus, Species)
            FilterTabRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it }
            )

            // Lista de espécies
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(speciesList) { species ->
                    SpeciesCard(species = species)
                }
            }
        }
    }
}

@Composable
fun FilterTabRow(
    selectedFilter: SpeciesFilter,
    onFilterSelected: (SpeciesFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
fun SpeciesCard(species: Species) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Navigate to species detail */ },
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Grid de imagens (2x2)
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(GeodouroLightBg)
                    )
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(GeodouroLightBg)
                    ) {
                        // Indicador de +N imagens
                        if (species.imageCount > 4) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "+${species.imageCount - 4}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(GeodouroLightBg)
                    )
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(GeodouroLightBg)
                    )
                }
            }

            // Informação da espécie
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
                
                // Família
                Surface(
                    color = GeodouroLightBg,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        species.family,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = GeodouroTextSecondary
                    )
                }
            }
        }
    }
}
