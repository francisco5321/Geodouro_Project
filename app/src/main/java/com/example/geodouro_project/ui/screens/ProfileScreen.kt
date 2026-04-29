package com.example.geodouro_project.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.example.geodouro_project.data.repository.PlantRepository
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.domain.model.SessionState
import com.example.geodouro_project.domain.model.ObservationSyncStatus
import com.example.geodouro_project.ui.theme.GeodouroBg
import com.example.geodouro_project.ui.theme.GeodouroBrandGreen
import com.example.geodouro_project.ui.theme.GeodouroCardBg
import com.example.geodouro_project.ui.theme.GeodouroDarkGreen
import com.example.geodouro_project.ui.theme.GeodouroGreen
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
import com.example.geodouro_project.ui.theme.geodouroOutlinedTextFieldColors
import com.example.geodouro_project.ui.theme.geodouroLoadingIndicatorColor
import com.example.geodouro_project.ui.theme.geodouroOutlinedBorderColor
import com.example.geodouro_project.ui.theme.geodouroOutlinedButtonColors
import com.example.geodouro_project.ui.theme.geodouroPrimaryButtonColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val observations: List<ObservationEntity> = emptyList(),
    val observationsCount: Int = 0,
    val publishedCount: Int = 0,
    val speciesCount: Int = 0,
    val isLoading: Boolean = true,
    val publishingIds: Set<String> = emptySet(),
    val statusMessage: String? = null
)

enum class ProfileObservationFilter(val label: String) {
    ALL("Todas"),
    PENDING("Pendentes"),
    SYNCED("Sincronizadas"),
    PUBLISHED("Publicadas")
}

class ProfileViewModel(
    private val repository: PlantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val observations = repository.fetchObservationsRemoteFirst(includeManualReview = true)
            val stats = repository.fetchObservationStatsRemoteFirst(includeManualReview = true)
            _uiState.value = _uiState.value.copy(
                observations = observations,
                observationsCount = stats.observationsCount,
                publishedCount = stats.publishedCount,
                speciesCount = stats.speciesCount,
                isLoading = false
            )
        }
    }

    fun publishObservation(observationId: String) {
        if (_uiState.value.publishingIds.contains(observationId)) {
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                publishingIds = _uiState.value.publishingIds + observationId,
                statusMessage = null
            )
            val published = runCatching {
                repository.publishObservation(observationId)
            }.getOrDefault(false)
            _uiState.value = _uiState.value.copy(
                publishingIds = _uiState.value.publishingIds - observationId,
                statusMessage = if (published) {
                    "Observação publicada com sucesso."
                } else {
                    "Não foi possível publicar. Confirma que a observação já foi sincronizada."
                }
            )
            if (published) {
                refresh()
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ProfileViewModel(
                        AppContainer.providePlantRepository(context)
                    ) as T
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ProfileScreen(
    refreshTrigger: Int = 0,
    sessionState: SessionState,
    onLogout: () -> Unit,
    onObservationClick: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.factory(context.applicationContext)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = { viewModel.refresh() }
    )
    val canPublishObservations = sessionState is SessionState.Authenticated
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(ProfileObservationFilter.ALL) }

    val filteredObservations = remember(uiState.observations, searchQuery, selectedFilter) {
        val normalizedQuery = searchQuery.trim().lowercase(Locale.ROOT)
        uiState.observations.filter { observation ->
            val matchesQuery = normalizedQuery.isBlank() ||
                listOf(
                    observation.enrichedScientificName,
                    observation.enrichedCommonName,
                    observation.predictedSpecies,
                    observation.enrichedFamily
                ).filterNotNull().any { it.lowercase(Locale.ROOT).contains(normalizedQuery) }

            val matchesFilter = when (selectedFilter) {
                ProfileObservationFilter.ALL -> true
                ProfileObservationFilter.PENDING -> {
                    !observation.isPublished && (
                        observation.requiresManualIdentification ||
                            observation.syncStatus != ObservationSyncStatus.SYNCED.name
                    )
                }
                ProfileObservationFilter.SYNCED -> {
                    !observation.isPublished &&
                        !observation.requiresManualIdentification &&
                        observation.syncStatus == ObservationSyncStatus.SYNCED.name
                }
                ProfileObservationFilter.PUBLISHED -> observation.isPublished
            }

            matchesQuery && matchesFilter
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
                    Text(
                        "Perfil",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = GeodouroBrandGreen
                    )
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(4.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        GeodouroBrandGreen,
                                        GeodouroGreen
                                    )
                                )
                            )
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            profileDisplayName(sessionState),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = GeodouroWhite
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = GeodouroWhite.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = profileSessionLabel(sessionState),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = GeodouroWhite
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatItem(
                                value = uiState.observationsCount.toString(),
                                label = "Observacoes",
                                modifier = Modifier.weight(1f)
                            )
                            StatItem(
                                value = uiState.publishedCount.toString(),
                                label = "Publicacoes",
                                modifier = Modifier.weight(1f)
                            )
                            StatItem(
                                value = uiState.speciesCount.toString(),
                                label = "Espécies",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GeodouroWhite,
                                contentColor = GeodouroBrandGreen
                            ),
                            shape = RoundedCornerShape(16.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 2.dp,
                                pressedElevation = 1.dp
                            )
                        ) {
                            Icon(
                                imageVector = if (sessionState is SessionState.Guest) {
                                    Icons.AutoMirrored.Filled.Login
                                } else {
                                    Icons.AutoMirrored.Filled.Logout
                                },
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                if (sessionState is SessionState.Guest) {
                                    "Fazer login"
                                } else {
                                    "Terminar sessão"
                                }
                            )
                        }
                    }
                }
                }

                item {
                Text(
                    "As minhas observacoes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GeodouroTextPrimary
                )
                }

                if (!canPublishObservations) {
                    item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = GeodouroLightBg,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Em modo convidado podes guardar observacoes, mas não podes transformá-las em publicacoes.",
                            modifier = Modifier.padding(12.dp),
                            color = GeodouroTextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    }
                }

                item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = geodouroOutlinedTextFieldColors(),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Limpar pesquisa"
                                )
                            }
                        }
                    },
                    placeholder = {
                        Text("Pesquisar observacoes")
                    }
                )
                }

                item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileObservationFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) },
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

                uiState.statusMessage?.let { message ->
                    item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = GeodouroLightBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(12.dp),
                            color = GeodouroTextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    }
                }

                if (uiState.observations.isEmpty()) {
                    item {
                    EmptyProfileState()
                    }
                } else if (filteredObservations.isEmpty()) {
                    item {
                    EmptyFilteredProfileState()
                    }
                } else {
                    items(filteredObservations, key = { it.id }) { observation ->
                    ObservationProfileCard(
                        observation = observation,
                        isPublishing = uiState.publishingIds.contains(observation.id),
                        canPublish = canPublishObservations,
                        onPublish = { viewModel.publishObservation(observation.id) },
                        onClick = { onObservationClick(observation.id) }
                    )
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = uiState.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = GeodouroBg,
                contentColor = GeodouroBrandGreen
            )
        }
    }
}

@Composable
private fun EmptyProfileState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "Ainda não tens observações guardadas.",
            modifier = Modifier.padding(16.dp),
            color = GeodouroTextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun EmptyFilteredProfileState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "Nenhuma observação corresponde aos filtros atuais.",
            modifier = Modifier.padding(16.dp),
            color = GeodouroTextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ObservationProfileCard(
    observation: ObservationEntity,
    isPublishing: Boolean,
    canPublish: Boolean,
    onPublish: () -> Unit,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageRequest = androidx.compose.runtime.remember(observation.imageUri) {
        ImageRequest.Builder(context)
            .data(observation.imageUri)
            .size(PROFILE_IMAGE_MAX_SIZE)
            .crossfade(false)
            .build()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AsyncImage(
                model = imageRequest,
                contentDescription = observation.predictedSpecies,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = observation.enrichedScientificName ?: observation.predictedSpecies,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GeodouroTextPrimary
            )

            Text(
                text = observation.enrichedCommonName ?: "Nome comum indisponível",
                style = MaterialTheme.typography.bodyMedium,
                color = GeodouroTextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = GeodouroLightBg,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = buildObservationMeta(observation),
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = GeodouroLightBg,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = buildProfileObservationStatusLabel(observation),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroBrandGreen
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (observation.isPublished) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = GeodouroGreen
                    )
                    Text(
                        text = "Já publicada na comunidade",
                        color = GeodouroGreen,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (canPublish) {
                Button(
                    onClick = onPublish,
                    enabled = !isPublishing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = geodouroPrimaryButtonColors()
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = geodouroLoadingIndicatorColor()
                        )
                    } else {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Transformar em publicação")
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = GeodouroLightBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Publicação indisponível em modo convidado.",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = GeodouroTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private fun buildObservationMeta(observation: ObservationEntity): String {
    val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        .format(Date(observation.capturedAt))
    val location = if (observation.latitude != null && observation.longitude != null) {
        "GPS %.5f, %.5f".format(observation.latitude, observation.longitude)
    } else {
        "Localizacao indisponível"
    }

    return "$date\n$location"
}

private fun buildProfileObservationStatusLabel(observation: ObservationEntity): String {
    return when {
        observation.isPublished -> "Publicada"
        observation.requiresManualIdentification -> "Em revisão"
        observation.syncStatus == ObservationSyncStatus.SYNCED.name -> "Sincronizada"
        observation.syncStatus == ObservationSyncStatus.FAILED.name -> "Guardada localmente - backend indisponivel"
        else -> "Pendente de sincronizacao"
    }
}

@Composable
fun StatItem(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = GeodouroCardBg
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = GeodouroDarkGreen
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = GeodouroTextSecondary
            )
        }
    }
}

private fun profileDisplayName(sessionState: SessionState): String {
    return when (sessionState) {
        is SessionState.Authenticated -> sessionState.displayName
        is SessionState.Guest -> sessionState.displayName
        SessionState.Loading,
        SessionState.LoggedOut -> "Utilizador"
    }
}

private fun profileSessionLabel(sessionState: SessionState): String {
    return when (sessionState) {
        is SessionState.Authenticated -> "Sessão autenticada"
        is SessionState.Guest -> "Modo convidado"
        SessionState.Loading,
        SessionState.LoggedOut -> "Sem sessão"
    }
}

private const val PROFILE_IMAGE_MAX_SIZE = 900
