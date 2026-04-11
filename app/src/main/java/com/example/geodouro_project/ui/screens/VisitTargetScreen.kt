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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geodouro_project.data.repository.AuthRepository
import com.example.geodouro_project.data.repository.VisitTargetRepository
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.domain.model.SessionState
import com.example.geodouro_project.ui.theme.GeodouroBrandGreen
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VisitTargetUiState {
    data object Loading : VisitTargetUiState
    data object GuestRestricted : VisitTargetUiState
    data object Empty : VisitTargetUiState
    data class Error(val message: String) : VisitTargetUiState
    data class Success(val targets: List<VisitTargetRepository.VisitTarget>) : VisitTargetUiState
}

class VisitTargetViewModel(
    private val visitTargetRepository: VisitTargetRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<VisitTargetUiState>(VisitTargetUiState.Loading)
    val uiState: StateFlow<VisitTargetUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = VisitTargetUiState.Loading
            when (val session = authRepository.sessionState.value) {
                is SessionState.Authenticated -> {
                    runCatching {
                        visitTargetRepository.fetchVisitTargets(session)
                    }.onSuccess { targets ->
                        _uiState.value = if (targets.isEmpty()) VisitTargetUiState.Empty else VisitTargetUiState.Success(targets)
                    }.onFailure { error ->
                        _uiState.value = VisitTargetUiState.Error(
                            error.message ?: "Nao foi possivel carregar Quero visitar."
                        )
                    }
                }
                is SessionState.Guest -> _uiState.value = VisitTargetUiState.GuestRestricted
                SessionState.LoggedOut, SessionState.Loading -> _uiState.value = VisitTargetUiState.Error("Sessao indisponivel.")
            }
        }
    }

    fun removeTarget(savedVisitTargetId: Int) {
        viewModelScope.launch {
            val session = authRepository.sessionState.value as? SessionState.Authenticated
                ?: run {
                    _uiState.value = VisitTargetUiState.Error("Sessao autenticada necessaria.")
                    return@launch
                }
            val removed = visitTargetRepository.deleteVisitTarget(savedVisitTargetId, session)
            if (removed) {
                refresh()
            } else {
                _uiState.value = VisitTargetUiState.Error("Nao foi possivel remover este alvo de visita.")
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext = context.applicationContext
                return VisitTargetViewModel(
                    visitTargetRepository = AppContainer.provideVisitTargetRepository(appContext),
                    authRepository = AppContainer.provideAuthRepository(appContext)
                ) as T
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitTargetScreen(
    onBackClick: () -> Unit,
    viewModel: VisitTargetViewModel = viewModel(
        factory = VisitTargetViewModel.factory(LocalContext.current.applicationContext)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Quero visitar",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = GeodouroBrandGreen
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = GeodouroBrandGreen)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = GeodouroWhite)
            )
        },
        containerColor = GeodouroWhite
    ) { padding ->
        when (val state = uiState) {
            VisitTargetUiState.Loading -> VisitTargetCenteredState(padding) {
                CircularProgressIndicator(color = GeodouroBrandGreen)
            }
            VisitTargetUiState.Empty -> VisitTargetMessageState(
                padding = padding,
                title = "Ainda sem alvos guardados",
                message = "Guarda especies, publicacoes ou observacoes para aparecerem aqui e depois planeares percursos."
            )
            is VisitTargetUiState.Error -> VisitTargetMessageState(
                padding = padding,
                title = "Nao foi possivel abrir Quero visitar",
                message = state.message,
                actionLabel = "Tentar novamente",
                onAction = viewModel::refresh
            )
            VisitTargetUiState.GuestRestricted -> VisitTargetMessageState(
                padding = padding,
                title = "Inicia sessao para guardar visitas",
                message = "O modo convidado permite explorar a app, mas a lista Quero visitar fica associada a uma conta autenticada."
            )
            is VisitTargetUiState.Success -> VisitTargetList(
                padding = padding,
                targets = state.targets,
                onRemove = viewModel::removeTarget
            )
        }
    }
}

@Composable
private fun VisitTargetList(
    padding: PaddingValues,
    targets: List<VisitTargetRepository.VisitTarget>,
    onRemove: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(GeodouroWhite),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Pontos guardados para futuros percursos",
                style = MaterialTheme.typography.bodyMedium,
                color = GeodouroTextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        items(targets, key = { it.savedVisitTargetId }) { target ->
            VisitTargetCard(target = target, onRemove = { onRemove(target.savedVisitTargetId) })
        }
    }
}

@Composable
private fun VisitTargetCard(
    target: VisitTargetRepository.VisitTarget,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = GeodouroWhite,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(GeodouroBrandGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (target.latitude != null && target.longitude != null) Icons.Default.LocationOn else Icons.Default.Eco,
                    contentDescription = null,
                    tint = GeodouroBrandGreen
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = target.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GeodouroTextPrimary
                )
                target.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = GeodouroTextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                RoutePlanMetaChip(target.targetType.asVisitTargetLabel())
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Remover", tint = GeodouroTextSecondary)
            }
        }
    }
}

@Composable
private fun VisitTargetMessageState(
    padding: PaddingValues,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    VisitTargetCenteredState(padding) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(GeodouroLightBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = GeodouroBrandGreen)
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = GeodouroTextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = GeodouroTextSecondary,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = GeodouroBrandGreen)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun VisitTargetCenteredState(
    padding: PaddingValues,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun String.asVisitTargetLabel(): String = when (lowercase()) {
    "observation" -> "Observacao"
    "publication" -> "Publicacao"
    "species" -> "Especie"
    else -> "Alvo"
}

