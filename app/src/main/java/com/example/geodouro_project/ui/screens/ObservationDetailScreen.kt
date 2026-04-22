package com.example.geodouro_project.ui.screens

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.example.geodouro_project.data.repository.PlantRepository
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.ui.components.GeoFloraHeaderLogo
import com.example.geodouro_project.ui.theme.GeodouroBg
import com.example.geodouro_project.ui.theme.GeodouroBrandGreen
import com.example.geodouro_project.ui.theme.GeodouroGreen
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
import com.example.geodouro_project.ui.theme.geodouroOutlinedTextFieldColors
import com.example.geodouro_project.ui.theme.geodouroLoadingIndicatorColor
import com.example.geodouro_project.ui.theme.geodouroPrimaryButtonColors
import com.example.geodouro_project.ui.theme.geodouroSecondaryButtonColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            _uiState.value = _uiState.value.copy(
                observation = repository.fetchRemoteObservationDetail(observationId)
                    ?: repository.fetchLocalObservations().firstOrNull { it.id == observationId },
                isLoading = false
            )
        }
    }

    fun saveManualIdentification(
        scientificName: String,
        commonName: String,
        family: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSaving = true,
                statusMessage = null
            )
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
                statusMessage = if (updated) {
                    "Observacao atualizada localmente."
                } else {
                    "Nao foi possivel atualizar esta observacao."
                }
            )
        }
    }

    companion object {
        fun factory(context: Context, observationId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ObservationDetailViewModel(
                        repository = AppContainer.providePlantRepository(context),
                        observationId = observationId
                    ) as T
                }
            }
    }
}

private fun buildObservationMetaForDetail(observation: ObservationEntity): String {
    val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        .format(Date(observation.capturedAt))
    val location = if (observation.latitude != null && observation.longitude != null) {
        "GPS %.5f, %.5f".format(observation.latitude, observation.longitude)
    } else {
        "Localizacao indisponivel"
    }

    return "$date\n$location"
}

private fun buildObservationStatusLabelForDetail(observation: ObservationEntity): String {
    return when {
        observation.isPublished -> "Publicada"
        observation.syncStatus == com.example.geodouro_project.domain.model.ObservationSyncStatus.SYNCED.name -> "Sincronizada"
        observation.syncStatus == com.example.geodouro_project.domain.model.ObservationSyncStatus.FAILED.name -> "Falha de sincronizacao"
        else -> "Pendente de sincronizacao"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationDetailScreen(
    observationId: String,
    onBackClick: () -> Unit,
    onOpenSpecies: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val viewModel: ObservationDetailViewModel = viewModel(
        factory = ObservationDetailViewModel.factory(context.applicationContext, observationId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val observation = uiState.observation
    val statusMessage = uiState.statusMessage
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

    LaunchedEffect(statusMessage, uiState.isSaving) {
        if (!uiState.isSaving && statusMessage == "Observacao atualizada localmente.") {
            isEditing = false
        }
    }

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
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(GeodouroBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text("A carregar observacao...", color = GeodouroTextSecondary)
                }
            }

            uiState.observation == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(GeodouroBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Observacao nao encontrada.", color = GeodouroTextSecondary)
                }
            }

            else -> {
                val observation = uiState.observation ?: return@Scaffold
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(GeodouroBg),
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
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (isEditing && !observation.isPublished) {
                                    OutlinedTextField(
                                        value = scientificNameInput,
                                        onValueChange = { scientificNameInput = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Nome cientifico") },
                                        singleLine = true,
                                        colors = geodouroOutlinedTextFieldColors()
                                    )
                                    OutlinedTextField(
                                        value = commonNameInput,
                                        onValueChange = { commonNameInput = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Nome comum") },
                                        singleLine = true,
                                        colors = geodouroOutlinedTextFieldColors()
                                    )
                                    OutlinedTextField(
                                        value = familyInput,
                                        onValueChange = { familyInput = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Familia") },
                                        singleLine = true,
                                        colors = geodouroOutlinedTextFieldColors()
                                    )
                                } else {
                                    Text(
                                        text = observation.enrichedScientificName ?: observation.predictedSpecies,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = GeodouroTextPrimary
                                    )
                                    Text(
                                        text = observation.enrichedCommonName ?: "Nome comum indisponivel",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = GeodouroTextSecondary
                                    )
                                }

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(observation.allImageUris()) { imageUri ->
                                        Box(
                                            modifier = Modifier
                                                .size(180.dp)
                                                .background(GeodouroLightBg, RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = imageUri,
                                                contentDescription = observation.predictedSpecies,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    color = GeodouroLightBg,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = buildObservationMetaForDetail(observation),
                                            color = GeodouroTextPrimary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = buildObservationStatusLabelForDetail(observation),
                                            color = GeodouroBrandGreen,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                if (!observation.isPublished) {
                                    Surface(
                                        color = GeodouroLightBg,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "Correcao manual antes de publicar",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = GeodouroTextPrimary
                                            )
                                            Text(
                                                text = "Podes ajustar os dados taxonomicos desta observacao antes de a transformares em publicacao.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = GeodouroTextSecondary
                                            )

                                            if (statusMessage != null) {
                                                Text(
                                                    text = statusMessage,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = GeodouroBrandGreen
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        if (isEditing) {
                                                            viewModel.saveManualIdentification(
                                                                scientificName = scientificNameInput,
                                                                commonName = commonNameInput,
                                                                family = familyInput
                                                            )
                                                        } else {
                                                            scientificNameInput = observation.enrichedScientificName
                                                                ?: observation.predictedSpecies
                                                            commonNameInput = observation.enrichedCommonName.orEmpty()
                                                            familyInput = observation.enrichedFamily.orEmpty()
                                                            isEditing = true
                                                        }
                                                    },
                                                    enabled = !uiState.isSaving && (
                                                        !isEditing || scientificNameInput.isNotBlank()
                                                    ),
                                                    modifier = Modifier.weight(1f),
                                                    colors = geodouroPrimaryButtonColors()
                                                ) {
                                                    if (uiState.isSaving) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(18.dp),
                                                            strokeWidth = 2.dp,
                                                            color = geodouroLoadingIndicatorColor()
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.size(8.dp))
                                                    Text(if (isEditing) "Guardar" else "Editar")
                                                }

                                                if (isEditing) {
                                                    Button(
                                                        onClick = {
                                                            scientificNameInput = observation.enrichedScientificName
                                                                ?: observation.predictedSpecies
                                                            commonNameInput = observation.enrichedCommonName.orEmpty()
                                                            familyInput = observation.enrichedFamily.orEmpty()
                                                            isEditing = false
                                                        },
                                                        enabled = !uiState.isSaving,
                                                        modifier = Modifier.weight(1f),
                                                        colors = geodouroSecondaryButtonColors()
                                                    ) {
                                                        Text("Cancelar")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            onOpenSpecies(
                                                (if (isEditing) scientificNameInput else observation.enrichedScientificName ?: observation.predictedSpecies).toSpeciesId()
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = geodouroPrimaryButtonColors()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text("Ver especie")
                                    }

                                    if (!observation.enrichedWikipediaUrl.isNullOrBlank()) {
                                        Button(
                                            onClick = { uriHandler.openUri(observation.enrichedWikipediaUrl) },
                                            modifier = Modifier.weight(1f),
                                            colors = geodouroSecondaryButtonColors()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Link,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.size(8.dp))
                                            Text("Wikipedia")
                                        }
                                    }
                                }

                                if (observation.isPublished) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Public,
                                            contentDescription = null,
                                            tint = GeodouroGreen
                                        )
                                        Text(
                                            text = "Esta observacao ja foi publicada na comunidade.",
                                            color = GeodouroGreen,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
