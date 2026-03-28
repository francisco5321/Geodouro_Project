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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.geodouro_project.data.local.entity.ObservationEntity
import com.example.geodouro_project.data.repository.PlantRepository
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.ui.theme.GeodouroBrandGreen
import com.example.geodouro_project.ui.theme.GeodouroGreen
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroLightGreen
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

data class ProfileUiState(
    val observations: List<ObservationEntity> = emptyList(),
    val observationsCount: Int = 0,
    val publishedCount: Int = 0,
    val speciesCount: Int = 0,
    val publishingIds: Set<String> = emptySet(),
    val statusMessage: String? = null
)

class ProfileViewModel(
    private val repository: PlantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeObservations().collect { observations ->
                _uiState.value = _uiState.value.copy(observations = observations)
            }
        }

        viewModelScope.launch {
            repository.observeObservationStats().collect { stats ->
                _uiState.value = _uiState.value.copy(
                    observationsCount = stats.observationsCount,
                    publishedCount = stats.publishedCount,
                    speciesCount = stats.speciesCount
                )
            }
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
                    "Observacao publicada com sucesso."
                } else {
                    "Nao foi possivel publicar. Confirma que a observacao ja foi sincronizada."
                }
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.factory(context.applicationContext)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Perfil",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = GeodouroBrandGreen
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GeodouroWhite
                )
            )
        }
    ) { padding ->
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
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(40.dp))
                                .background(GeodouroLightGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Utilizador",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = GeodouroTextPrimary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(uiState.observationsCount.toString(), "Observacoes")
                            StatItem(uiState.publishedCount.toString(), "Publicacoes")
                            StatItem(uiState.speciesCount.toString(), "Especies")
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
            } else {
                items(uiState.observations, key = { it.id }) { observation ->
                    ObservationProfileCard(
                        observation = observation,
                        isPublishing = uiState.publishingIds.contains(observation.id),
                        onPublish = { viewModel.publishObservation(observation.id) }
                    )
                }
            }
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
            text = "Ainda nao tens observacoes guardadas.",
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
    onPublish: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AsyncImage(
                model = observation.imageUri,
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
                text = observation.enrichedCommonName ?: "Nome comum indisponivel",
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
                        text = "Ja publicada na comunidade",
                        color = GeodouroGreen,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onPublish,
                    enabled = !isPublishing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Transformar em publicacao")
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
        "Localizacao indisponivel"
    }

    return "$date\n$location"
}

@Composable
fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = GeodouroGreen
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = GeodouroTextSecondary
        )
    }
}
