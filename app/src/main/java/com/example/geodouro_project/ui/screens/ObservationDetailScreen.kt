package com.example.geodouro_project.ui.screens

import android.location.Geocoder
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.example.geodouro_project.data.repository.PlantRepository
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.domain.model.SessionState
import com.example.geodouro_project.ui.components.GeoFloraHeaderLogo
import com.example.geodouro_project.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// -----------------------------------------------------------------------------
// Design tokens (local to this file)
// Design tokens (local to this file)
private val GreenHero      = Color(0xFF2D6A4F)
private val GreenLight     = Color(0xFF52B788)
private val GreenPale      = Color(0xFFD8F3DC)
private val GreenTag       = Color(0xFFEBF7EE)
private val GreenMid       = Color(0xFF40916C)
private val TextPrimary    = Color(0xFF1B2E24)
private val TextSecondary  = Color(0xFF5A7265)
private val TextMuted      = Color(0xFF8FA99A)
private val ScreenBg       = Color(0xFFF4F7F2)
private val CardBg         = Color(0xFFFFFFFF)
private val BorderColor    = Color(0x1F2D6A4F)   // ~12% alpha green
private val CardRadius     = RoundedCornerShape(20.dp)
private val ChipRadius     = RoundedCornerShape(8.dp)
private val ButtonRadius   = RoundedCornerShape(12.dp)

// -----------------------------------------------------------------------------
// ViewModel (unchanged logic)
// ViewModel (unchanged logic)
data class ObservationDetailUiState(
    val observation: ObservationEntity? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val statusMessage: String? = null
)

class ObservationDetailViewModel(
    private val repository: PlantRepository,
    private val observationId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ObservationDetailUiState())
    val uiState: StateFlow<ObservationDetailUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val localObservation = repository.fetchLocalObservationById(observationId)
            val remoteObservation = repository.fetchRemoteObservationDetail(observationId)
            _uiState.value = _uiState.value.copy(
                observation = remoteObservation?.copy(
                    notes = remoteObservation.notes ?: localObservation?.notes
                ) ?: localObservation,
                isLoading = false
            )
        }
    }

    fun saveManualIdentification(scientificName: String, commonName: String, family: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, statusMessage = null)
            val updated = runCatching {
                repository.updateObservationMetadata(
                    observationId = observationId,
                    scientificName = scientificName,
                    commonName = commonName,
                    family = family
                )
            }.getOrDefault(false)
            if (updated) refresh()
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                statusMessage = if (updated) "Observação atualizada." else "Não foi possível atualizar."
            )
        }
    }

    companion object {
        fun factory(context: Context, observationId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ObservationDetailViewModel(
                        repository = AppContainer.providePlantRepository(context),
                        observationId = observationId
                    ) as T
            }
    }
}

// -----------------------------------------------------------------------------
// Helpers
// Helpers
private fun formatObservationDate(observation: ObservationEntity): String =
    SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
        .format(Date(observation.capturedAt))

private fun formatSyncStatus(observation: ObservationEntity): String = when {
    observation.isPublished -> "Publicada"
    observation.syncStatus == com.example.geodouro_project.domain.model.ObservationSyncStatus.SYNCED.name -> "Sincronizada"
    observation.syncStatus == com.example.geodouro_project.domain.model.ObservationSyncStatus.FAILED.name -> "Falha de sincronização"
    else -> "Pendente"
}

private data class ObservationLocationContext(
    val primaryLabel: String,
    val coordinatesLabel: String?
)

@Composable
private fun rememberObservationLocationContext(observation: ObservationEntity?): ObservationLocationContext {
    val context = LocalContext.current
    return produceState(
        initialValue = buildFallbackLocationContext(observation),
        key1 = observation?.id,
        key2 = observation?.latitude,
        key3 = observation?.longitude
    ) { value = resolveObservationLocationContext(context, observation) }.value
}

private suspend fun resolveObservationLocationContext(
    context: Context,
    observation: ObservationEntity?
): ObservationLocationContext = withContext(Dispatchers.IO) {
    val fallback = buildFallbackLocationContext(observation)
    val lat = observation?.latitude ?: return@withContext fallback
    val lon = observation.longitude ?: return@withContext fallback
    if (!Geocoder.isPresent()) return@withContext fallback
    val geocoder = Geocoder(context, Locale.getDefault())
    @Suppress("DEPRECATION")
    val address = runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
    val label = buildList {
        address?.thoroughfare?.takeIf { it.isNotBlank() }?.let(::add)
        address?.subLocality?.takeIf { it.isNotBlank() }?.let(::add)
        address?.locality?.takeIf { it.isNotBlank() }?.let(::add)
        address?.adminArea?.takeIf { it.isNotBlank() }?.let(::add)
    }.distinct().joinToString(", ")
    if (label.isBlank()) fallback else fallback.copy(primaryLabel = label)
}

private fun buildFallbackLocationContext(observation: ObservationEntity?): ObservationLocationContext {
    val lat = observation?.latitude
    val lon = observation?.longitude
    return if (lat != null && lon != null) {
        ObservationLocationContext("Localização com GPS", "%.5f, %.5f".format(lat, lon))
    } else {
        ObservationLocationContext("Localização indisponível", null)
    }
}

// -----------------------------------------------------------------------------
// Main Screen
// Screen
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ObservationDetailScreen(
    refreshTrigger: Int = 0,
    observationId: String,
    hideSpeciesAction: Boolean = false,
    sessionState: SessionState,
    onBackClick: () -> Unit,
    onOpenSpecies: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val viewModel: ObservationDetailViewModel = viewModel(
        factory = ObservationDetailViewModel.factory(context.applicationContext, observationId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = { viewModel.refresh() }
    )

    val observation = uiState.observation
    val locationContext = rememberObservationLocationContext(observation)

    val canEdit = remember(observation, sessionState) {
        val obs = observation ?: return@remember false
        if (obs.isPublished) return@remember false
        when (sessionState) {
            is SessionState.Authenticated ->
                sessionState.userId != null && obs.ownerUserId == sessionState.userId
            is SessionState.Guest ->
                !obs.ownerGuestLabel.isNullOrBlank() && obs.ownerGuestLabel == sessionState.guestLabel
            else -> false
        }
    }

    var isEditing by rememberSaveable(observation?.id) { mutableStateOf(false) }
    var scientificNameInput by rememberSaveable(observation?.id) {
        mutableStateOf(observation?.enrichedScientificName ?: observation?.predictedSpecies.orEmpty())
    }
    var commonNameInput by rememberSaveable(observation?.id) {
        mutableStateOf(observation?.enrichedCommonName.orEmpty())
    }
    var familyInput by rememberSaveable(observation?.id) {
        mutableStateOf(observation?.enrichedFamily.orEmpty())
    }

    LaunchedEffect(uiState.statusMessage, uiState.isSaving) {
        if (!uiState.isSaving && uiState.statusMessage == "Observação atualizada.") isEditing = false
    }
    LaunchedEffect(refreshTrigger, observationId) {
        if (refreshTrigger > 0) viewModel.refresh()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                expandedHeight = 48.dp,
                title = { GeoFloraHeaderLogo() },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = GreenHero
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = CardBg
                )
            )
        },
        containerColor = ScreenBg
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = GreenHero, strokeWidth = 2.dp)
                            Text("A carregar observação...", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
                observation == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Observação não encontrada.", color = TextSecondary)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Hero card
                        item {
                            HeroCard(
                                observation = observation,
                                isEditing = isEditing,
                                scientificNameInput = scientificNameInput,
                                onScientificNameChange = { scientificNameInput = it },
                                commonNameInput = commonNameInput,
                                onCommonNameChange = { commonNameInput = it },
                                locationContext = locationContext
                            )
                        }

                        // Taxonomic context
                        item {
                            SectionLabel("Contexto taxonómico")
                            Spacer(Modifier.height(6.dp))
                            TaxonomicContextCard(
                                observation = observation,
                                locationContext = locationContext,
                                onOpenWikipedia = observation.enrichedWikipediaUrl
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { url -> { uriHandler.openUri(url) } }
                            )
                        }

                        // Manual edit
                        observation.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                            item {
                                SectionLabel("Descrição")
                                Spacer(Modifier.height(6.dp))
                                ObservationNotesCard(notes = notes)
                            }
                        }
                        if (canEdit) {
                            item {
                                SectionLabel("Identificação manual")
                                Spacer(Modifier.height(6.dp))
                                ManualEditCard(
                                    observation = observation,
                                    isEditing = isEditing,
                                    isSaving = uiState.isSaving,
                                    statusMessage = uiState.statusMessage,
                                    scientificNameInput = scientificNameInput,
                                    onScientificNameChange = { scientificNameInput = it },
                                    commonNameInput = commonNameInput,
                                    onCommonNameChange = { commonNameInput = it },
                                    familyInput = familyInput,
                                    onFamilyChange = { familyInput = it },
                                    onEditToggle = {
                                        if (isEditing) {
                                            viewModel.saveManualIdentification(scientificNameInput, commonNameInput, familyInput)
                                        } else {
                                            scientificNameInput = observation.enrichedScientificName ?: observation.predictedSpecies
                                            commonNameInput = observation.enrichedCommonName.orEmpty()
                                            familyInput = observation.enrichedFamily.orEmpty()
                                            isEditing = true
                                        }
                                    },
                                    onCancel = {
                                        scientificNameInput = observation.enrichedScientificName ?: observation.predictedSpecies
                                        commonNameInput = observation.enrichedCommonName.orEmpty()
                                        familyInput = observation.enrichedFamily.orEmpty()
                                        isEditing = false
                                    }
                                )
                            }
                        }

                        if (!hideSpeciesAction) {
                            item {
                                GeoButton(
                                    label = "Ver espécie",
                                    icon = Icons.Default.Image,
                                    primary = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        onOpenSpecies(
                                            (if (isEditing) scientificNameInput
                                            else observation.enrichedScientificName ?: observation.predictedSpecies)
                                                .toSpeciesId()
                                        )
                                    }
                                )
                            }
                        }

                        if (observation.isPublished) {
                            item { PublishedBanner() }
                        }

                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = uiState.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = CardBg,
                contentColor = GreenHero
            )
        }
    }
}

@Composable
private fun HeroCard(
    observation: ObservationEntity,
    isEditing: Boolean,
    scientificNameInput: String,
    onScientificNameChange: (String) -> Unit,
    commonNameInput: String,
    onCommonNameChange: (String) -> Unit,
    locationContext: ObservationLocationContext
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardRadius,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(0.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderColor)
    ) {
        Column {
            // Image header with gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(
                        Brush.linearGradient(listOf(GreenHero, GreenLight))
                    )
            ) {
                val images = observation.allImageUris()
                if (images.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(images) { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = observation.predictedSpecies,
                                modifier = Modifier
                                    .height(166.dp)
                                    .aspectRatio(0.85f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, Color.White.copy(0.25f), RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                // Status pill
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    color = CardBg.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(GreenLight, CircleShape)
                        )
                        Text(
                            text = formatSyncStatus(observation),
                            color = GreenHero,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Species info
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isEditing && !observation.isPublished) {
                    OutlinedTextField(
                        value = scientificNameInput,
                        onValueChange = onScientificNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nome científico") },
                        singleLine = true,
                        colors = geodouroOutlinedTextFieldColors()
                    )
                    OutlinedTextField(
                        value = commonNameInput,
                        onValueChange = onCommonNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nome comum") },
                        singleLine = true,
                        colors = geodouroOutlinedTextFieldColors()
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = observation.enrichedScientificName ?: observation.predictedSpecies,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            color = TextPrimary,
                            lineHeight = 26.sp
                        )
                        val commonName = observation.enrichedCommonName?.takeIf { it.isNotBlank() }
                        val family = observation.enrichedFamily?.takeIf { it.isNotBlank() }
                        val subtitle = listOfNotNull(commonName, family?.let { "Família $it" })
                            .joinToString(" • ")
                            .ifBlank { "Nome comum indisponível" }
                        Text(
                            text = subtitle,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                // Meta chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        MetaChip(
                            icon = Icons.Default.LocationOn,
                            label = locationContext.primaryLabel
                        )
                    }
                    item {
                        MetaChip(
                            icon = Icons.Default.DateRange,
                            label = formatObservationDate(observation)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxonomicContextCard(
    observation: ObservationEntity,
    locationContext: ObservationLocationContext,
    onOpenWikipedia: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardRadius,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(0.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderColor)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(GreenPale, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = GreenHero, modifier = Modifier.size(16.dp))
                }
                Text("Informação da espécie", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }

            HorizontalDivider(thickness = 0.5.dp, color = BorderColor)

            // 2x2 grid via two rows
            val speciesValue = observation.enrichedCommonName?.takeIf { it.isNotBlank() }
                ?: observation.enrichedScientificName
                ?: observation.predictedSpecies

            Column {
                Row(Modifier.fillMaxWidth()) {
                    ContextCell(
                        modifier = Modifier.weight(1f),
                        label = "Espécie",
                        value = speciesValue,
                        borderRight = true,
                        borderBottom = true
                    )
                    ContextCell(
                        modifier = Modifier.weight(1f),
                        label = "Família",
                        value = observation.enrichedFamily ?: "Indisponível",
                        borderRight = false,
                        borderBottom = true
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    ContextCell(
                        modifier = Modifier.weight(1f),
                        label = "Localização",
                        value = locationContext.primaryLabel,
                        supporting = locationContext.coordinatesLabel,
                        icon = Icons.Default.LocationOn,
                        borderRight = true,
                        borderBottom = false
                    )
                    ContextCellLink(
                        modifier = Modifier.weight(1f),
                        label = "Wikipedia",
                        linkLabel = if (onOpenWikipedia != null) "Abrir referência" else "Sem referência",
                        enabled = onOpenWikipedia != null,
                        onClick = onOpenWikipedia,
                        borderBottom = false
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    supporting: String? = null,
    icon: ImageVector? = null,
    borderRight: Boolean = false,
    borderBottom: Boolean = false
) {
    Box(
        modifier = modifier
            .then(if (borderRight) Modifier.border(width = 0.5.dp, color = BorderColor, shape = RoundedCornerShape(0.dp)) else Modifier)
    ) {
        // Use manual padding + dividers rather than nested borders to avoid shape conflict
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.07.sp, color = TextMuted)
            if (icon != null) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(icon, contentDescription = null, tint = GreenHero, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!supporting.isNullOrBlank()) Text(supporting, fontSize = 10.sp, color = TextMuted)
                    }
                }
            } else {
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!supporting.isNullOrBlank()) Text(supporting, fontSize = 10.sp, color = TextMuted)
            }
        }
        if (borderRight) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(0.5.dp)
                    .background(BorderColor)
            )
        }
        if (borderBottom) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(BorderColor)
            )
        }
    }
}

@Composable
private fun ContextCellLink(
    modifier: Modifier = Modifier,
    label: String,
    linkLabel: String,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    borderBottom: Boolean = false
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .then(if (enabled && onClick != null) Modifier.clickable { onClick() } else Modifier),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.07.sp, color = TextMuted)
            Text(
                text = linkLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) GreenHero else TextMuted,
                textDecoration = if (enabled) TextDecoration.Underline else TextDecoration.None,
                maxLines = 2
            )
        }
        if (borderBottom) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(BorderColor)
            )
        }
    }
}

// Manual edit card
@Composable
private fun ManualEditCard(
    observation: ObservationEntity,
    isEditing: Boolean,
    isSaving: Boolean,
    statusMessage: String?,
    scientificNameInput: String,
    onScientificNameChange: (String) -> Unit,
    commonNameInput: String,
    onCommonNameChange: (String) -> Unit,
    familyInput: String,
    onFamilyChange: (String) -> Unit,
    onEditToggle: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardRadius,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(0.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(GreenPale, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = GreenHero, modifier = Modifier.size(14.dp))
                }
                Text("Correção antes de publicar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }

            Text(
                "Podes ajustar os dados taxonómicos desta observação antes de a transformares em publicação.",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            if (isEditing) {
                OutlinedTextField(
                    value = scientificNameInput,
                    onValueChange = onScientificNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome científico") },
                    singleLine = true,
                    colors = geodouroOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = commonNameInput,
                    onValueChange = onCommonNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome comum") },
                    singleLine = true,
                    colors = geodouroOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = familyInput,
                    onValueChange = onFamilyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Família") },
                    singleLine = true,
                    colors = geodouroOutlinedTextFieldColors()
                )
            }

            if (statusMessage != null) {
                Text(statusMessage, fontSize = 12.sp, color = GreenMid, fontWeight = FontWeight.Medium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onEditToggle,
                    enabled = !isSaving && (!isEditing || scientificNameInput.isNotBlank()),
                    modifier = Modifier.weight(1f),
                    shape = ButtonRadius,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenHero, contentColor = Color.White)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (isEditing) "Guardar" else "Editar", fontSize = 13.sp)
                }
                if (isEditing) {
                    Button(
                        onClick = onCancel,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = ButtonRadius,
                        colors = ButtonDefaults.buttonColors(containerColor = GreenTag, contentColor = GreenHero)
                    ) {
                        Text("Cancelar", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// Published banner
@Composable
private fun PublishedBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GreenPale, RoundedCornerShape(14.dp))
            .border(0.5.dp, GreenHero.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(GreenHero, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Public, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Text(
            text = "Esta observação já foi publicada na comunidade GeoFlora.",
            fontSize = 12.sp,
            color = GreenHero,
            fontWeight = FontWeight.Medium,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// Shared small components
@Composable
private fun ObservationNotesCard(notes: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = CardRadius,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Text(
            text = notes,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = TextSecondary
        )
    }
}

// Shared small components
@Composable
private fun MetaChip(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .background(GreenTag, ChipRadius)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = GreenHero, modifier = Modifier.size(11.dp))
        Text(label, fontSize = 11.sp, color = GreenHero, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GeoButton(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = ButtonRadius,
        colors = if (primary) {
            ButtonDefaults.buttonColors(containerColor = GreenHero, contentColor = Color.White)
        } else {
            ButtonDefaults.buttonColors(containerColor = GreenTag, contentColor = GreenHero)
        },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.08.sp,
        color = TextMuted,
        modifier = Modifier.padding(horizontal = 2.dp)
    )
}
