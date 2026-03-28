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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun CommunityScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: CommunityViewModel = viewModel(
        factory = CommunityViewModel.factory(context.applicationContext)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = { viewModel.refresh() }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Comunidade",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GeodouroWhite)
                .pullRefresh(pullRefreshState)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.isLoading) {
                    item {
                        Text(
                            text = "A carregar publicacoes...",
                            color = GeodouroTextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (uiState.publications.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
                            elevation = CardDefaults.cardElevation(2.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Ainda nao existem publicacoes na comunidade.",
                                modifier = Modifier.padding(16.dp),
                                color = GeodouroTextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(uiState.publications, key = { it.id }) { post ->
                        CommunityPostCard(post)
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

@Composable
fun CommunityPostCard(post: CommunityPublication) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(GeodouroLightGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        post.userDisplayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = GeodouroTextPrimary
                    )
                    Text(
                        formatTimeAgo(post.publishedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = GeodouroTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AsyncImage(
                model = post.imageUrl,
                contentDescription = post.scientificName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(GeodouroLightBg),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = post.scientificName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GeodouroTextPrimary
            )

            Text(
                text = post.commonName ?: "Nome comum indisponivel",
                style = MaterialTheme.typography.bodyMedium,
                color = GeodouroTextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = GeodouroTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = formatLocation(post),
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroTextSecondary
                )
            }
        }
    }
}

private fun formatLocation(observation: CommunityPublication): String {
    return if (observation.latitude != null && observation.longitude != null) {
        "GPS %.5f, %.5f".format(observation.latitude, observation.longitude)
    } else {
        "Localizacao indisponivel"
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
        minutes < 60 -> "ha ${minutes} min"
        hours < 24 -> "ha ${hours} h"
        else -> "ha ${days} d"
    }
}
