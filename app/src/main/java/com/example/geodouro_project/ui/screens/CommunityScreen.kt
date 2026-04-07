package com.example.geodouro_project.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.geodouro_project.data.repository.PlantRepository
import com.example.geodouro_project.data.repository.PlantRepository.CommunityPublication
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.ui.theme.GeodouroBrandGreen
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroLightGreen
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ──────────────────────────────────────────────
// ViewModel (sem alterações)
// ──────────────────────────────────────────────

class CommunityViewModel(
    private val repository: PlantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val publications = repository.fetchCommunityPublications()
            _uiState.value = CommunityUiState(
                publications = publications,
                isLoading = false
            )
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CommunityViewModel(
                        AppContainer.providePlantRepository(context)
                    ) as T
                }
            }
    }
}

data class CommunityUiState(
    val publications: List<CommunityPublication> = emptyList(),
    val isLoading: Boolean = true
)

enum class CommunityFilter(val label: String) {
    ALL("Todas"),
    WITH_LOCATION("Com GPS"),
    WITHOUT_LOCATION("Sem GPS")
}

// ──────────────────────────────────────────────
// Screen
// ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun CommunityScreen(
    refreshTrigger: Int = 0,
    onSpeciesClick: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: CommunityViewModel = viewModel(
        factory = CommunityViewModel.factory(context.applicationContext)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(CommunityFilter.ALL) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = { viewModel.refresh() }
    )

    androidx.compose.runtime.LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.refresh()
    }

    val filteredPublications = uiState.publications.filter { publication ->
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        val matchesQuery = query.isBlank() ||
            publication.scientificName.lowercase(Locale.ROOT).contains(query) ||
            (publication.commonName?.lowercase(Locale.ROOT)?.contains(query) == true) ||
            publication.userDisplayName.lowercase(Locale.ROOT).contains(query)

        val matchesFilter = when (selectedFilter) {
            CommunityFilter.ALL -> true
            CommunityFilter.WITH_LOCATION -> publication.latitude != null && publication.longitude != null
            CommunityFilter.WITHOUT_LOCATION -> publication.latitude == null || publication.longitude == null
        }

        matchesQuery && matchesFilter
    }

    Scaffold(
        topBar = {
            // Top bar com gradiente sutil e sombra
            Surface(
                shadowElevation = 4.dp,
                color = GeodouroWhite
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Comunidade",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = GeodouroBrandGreen,
                                letterSpacing = 0.5.sp
                            )
                            if (!uiState.isLoading) {
                                Text(
                                    text = "${uiState.publications.size} observações partilhadas",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GeodouroTextSecondary,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GeodouroLightBg)
                .pullRefresh(pullRefreshState)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ── Barra de pesquisa + filtros (fundo branco fixo) ──
                item {
                    Surface(
                        color = GeodouroWhite,
                        shadowElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Campo de pesquisa
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color(0xFFDDE8DC),
                                    focusedBorderColor = GeodouroBrandGreen,
                                    unfocusedContainerColor = GeodouroLightBg,
                                    focusedContainerColor = GeodouroWhite
                                ),
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = GeodouroBrandGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = {
                                    AnimatedVisibility(
                                        visible = searchQuery.isNotBlank(),
                                        enter = fadeIn(),
                                        exit = fadeOut()
                                    ) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Limpar pesquisa",
                                                tint = GeodouroTextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                placeholder = {
                                    Text(
                                        "Pesquisar publicações…",
                                        color = GeodouroTextSecondary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            )

                            // Filtros em LazyRow para evitar quebra de linha
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(CommunityFilter.entries) { filter ->
                                    FilterChip(
                                        selected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter },
                                        label = {
                                            Text(
                                                filter.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (selectedFilter == filter)
                                                    FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GeodouroBrandGreen,
                                            selectedLabelColor = Color.White,
                                            containerColor = GeodouroLightBg,
                                            labelColor = GeodouroTextSecondary
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = selectedFilter == filter,
                                            borderColor = Color(0xFFDDE8DC),
                                            selectedBorderColor = Color.Transparent
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Espaço entre header e lista ──
                item { Spacer(Modifier.height(12.dp)) }

                // ── Estados: loading / vazio / sem resultados ──
                when {
                    uiState.isLoading -> {
                        item {
                            EmptyStateCard(
                                message = "A carregar publicações…",
                                isLoading = true
                            )
                        }
                    }
                    uiState.publications.isEmpty() -> {
                        item {
                            EmptyStateCard(
                                message = "Ainda não existem publicações na comunidade.",
                                isLoading = false
                            )
                        }
                    }
                    filteredPublications.isEmpty() -> {
                        item {
                            EmptyStateCard(
                                message = "Nenhuma publicação corresponde aos filtros atuais.",
                                isLoading = false
                            )
                        }
                    }
                    else -> {
                        items(
                            items = filteredPublications,
                            key = { it.id }
                        ) { post ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 })
                            ) {
                                CommunityPostCard(
                                    post = post,
                                    onClick = { onSpeciesClick(post.scientificName.toSpeciesId()) }
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = uiState.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = GeodouroWhite,
                contentColor = GeodouroBrandGreen
            )
        }
    }
}

// ──────────────────────────────────────────────
// Empty / loading state
// ──────────────────────────────────────────────

@Composable
private fun EmptyStateCard(message: String, isLoading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
            elevation = CardDefaults.cardElevation(1.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GeodouroLightBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = GeodouroBrandGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = message,
                    color = GeodouroTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// ──────────────────────────────────────────────
// Post card — redesenhado
// ──────────────────────────────────────────────

@Composable
fun CommunityPostCard(
    post: CommunityPublication,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column {
            // ── Imagem com gradiente sobreposto em baixo ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            ) {
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = post.scientificName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(GeodouroLightBg),
                    contentScale = ContentScale.Crop
                )

                // Gradiente escuro na base da imagem
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.45f)
                                )
                            )
                        )
                )

                // Badge de GPS sobre a imagem
                if (post.latitude != null && post.longitude != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        color = GeodouroBrandGreen.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "GPS",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // ── Conteúdo textual ──
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Nome científico + comum
                Text(
                    text = post.scientificName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = GeodouroTextPrimary,
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = post.commonName ?: "Nome comum indisponível",
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroBrandGreen,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = GeodouroLightBg, thickness = 1.dp)
                Spacer(Modifier.height(4.dp))

                // Autor + tempo + localização
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(GeodouroLightGreen, GeodouroBrandGreen)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = post.userDisplayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = GeodouroTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatTimeAgo(post.publishedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = GeodouroTextSecondary
                        )
                    }

                    // Coordenadas (apenas se disponíveis)
                    if (post.latitude != null && post.longitude != null) {
                        Text(
                            text = "%.4f, %.4f".format(post.latitude, post.longitude),
                            style = MaterialTheme.typography.labelSmall,
                            color = GeodouroTextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// Helpers (sem alterações)
// ──────────────────────────────────────────────

private fun formatLocation(observation: CommunityPublication): String {
    return if (observation.latitude != null && observation.longitude != null) {
        "GPS %.5f, %.5f".format(observation.latitude, observation.longitude)
    } else {
        "Localização indisponível"
    }
}

private fun formatTimeAgo(timestamp: String): String {
    val publishedAtMillis = runCatching { java.time.Instant.parse(timestamp).toEpochMilli() }
        .getOrDefault(System.currentTimeMillis())
    val diff = System.currentTimeMillis() - publishedAtMillis
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        minutes < 1 -> "agora mesmo"
        minutes < 60 -> "há ${minutes} min"
        hours < 24 -> "há ${hours} h"
        else -> "há ${days} d"
    }
}