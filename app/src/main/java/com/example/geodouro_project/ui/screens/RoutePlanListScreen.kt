package com.example.geodouro_project.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geodouro_project.data.repository.RoutePlanRepository
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.domain.model.SessionState
import com.example.geodouro_project.ui.theme.GeodouroBg
import com.example.geodouro_project.ui.theme.GeodouroBrandGreen
import com.example.geodouro_project.ui.theme.GeodouroGreen
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RoutePlanListUiState {
    data object Loading : RoutePlanListUiState
    data object GuestRestricted : RoutePlanListUiState
    data class Error(val message: String) : RoutePlanListUiState
    data object Empty : RoutePlanListUiState
    data class Success(val routePlans: List<RoutePlanRepository.RoutePlanSummary>) : RoutePlanListUiState
}

class RoutePlanListViewModel(
    private val routePlanRepository: RoutePlanRepository,
    private val sessionStateProvider: () -> SessionState
) : ViewModel() {
    private val _uiState = MutableStateFlow<RoutePlanListUiState>(RoutePlanListUiState.Loading)
    val uiState: StateFlow<RoutePlanListUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = RoutePlanListUiState.Loading
            when (val sessionState = sessionStateProvider()) {
                is SessionState.Authenticated -> {
                    val routePlans = runCatching {
                        routePlanRepository.fetchRoutePlans(sessionState)
                    }.getOrElse { error ->
                        _uiState.value = RoutePlanListUiState.Error(
                            error.message ?: "Nao foi possivel carregar os percursos."
                        )
                        return@launch
                    }

                    _uiState.value = if (routePlans.isEmpty()) {
                        RoutePlanListUiState.Empty
                    } else {
                        RoutePlanListUiState.Success(routePlans)
                    }
                }

                is SessionState.Guest -> {
                    _uiState.value = RoutePlanListUiState.GuestRestricted
                }

                SessionState.Loading,
                SessionState.LoggedOut -> {
                    _uiState.value = RoutePlanListUiState.Error("Sessao indisponivel.")
                }
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    val authRepository = AppContainer.provideAuthRepository(appContext)
                    return RoutePlanListViewModel(
                        routePlanRepository = AppContainer.provideRoutePlanRepository(appContext),
                        sessionStateProvider = authRepository::currentSessionState
                    ) as T
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlanListScreen(
    onRoutePlanClick: (Int) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: RoutePlanListViewModel = viewModel(
        factory = RoutePlanListViewModel.factory(context.applicationContext)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Percursos",
                        style = MaterialTheme.typography.titleLarge,
                        color = GeodouroBrandGreen,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GeodouroBg
                )
            )
        },
        containerColor = GeodouroBg
    ) { padding ->
        when (val state = uiState) {
            RoutePlanListUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            RoutePlanListUiState.Empty -> {
                RoutePlanEmptyState(
                    title = "Ainda nao existem percursos planeados.",
                    message = "Os percursos criados na web vao aparecer aqui quando estiverem associados a esta conta.",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            is RoutePlanListUiState.Error -> {
                RoutePlanEmptyState(
                    title = "Nao foi possivel carregar os percursos.",
                    message = state.message,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            RoutePlanListUiState.GuestRestricted -> {
                RoutePlanEmptyState(
                    title = "Percursos disponiveis apenas com sessao autenticada.",
                    message = "Entra com a tua conta para veres os percursos planeados criados na web.",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            is RoutePlanListUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(GeodouroBg),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.routePlans, key = { it.routePlanId }) { routePlan ->
                        RoutePlanSummaryCard(
                            routePlan = routePlan,
                            onClick = { onRoutePlanClick(routePlan.routePlanId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutePlanSummaryCard(
    routePlan: RoutePlanRepository.RoutePlanSummary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = routePlan.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GeodouroTextPrimary
                    )
                    routePlan.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = GeodouroTextSecondary,
                            maxLines = 2
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = GeodouroBrandGreen
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoutePlanMetaChip("${routePlan.stopCount} paragens")
                RoutePlanMetaChip(
                    routePlan.startLabel?.takeIf { it.isNotBlank() } ?: "Inicio na primeira paragem"
                )
            }
        }
    }
}

@Composable
fun RoutePlanMetaChip(label: String) {
    Surface(
        color = GeodouroLightBg,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = GeodouroTextSecondary
        )
    }
}

@Composable
fun RoutePlanEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = GeodouroLightBg
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    tint = GeodouroGreen,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(28.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = GeodouroTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = GeodouroTextSecondary
            )
        }
    }
}
