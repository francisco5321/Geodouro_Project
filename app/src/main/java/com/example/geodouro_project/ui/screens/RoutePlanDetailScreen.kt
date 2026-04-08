package com.example.geodouro_project.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.geodouro_project.ui.theme.GeodouroBrandGreen
import com.example.geodouro_project.ui.theme.GeodouroGreen
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RoutePlanDetailUiState {
    data object Loading : RoutePlanDetailUiState
    data object GuestRestricted : RoutePlanDetailUiState
    data class Error(val message: String) : RoutePlanDetailUiState
    data class Success(val routePlan: RoutePlanRepository.RoutePlanDetail) : RoutePlanDetailUiState
}

class RoutePlanDetailViewModel(
    private val routePlanId: Int,
    private val routePlanRepository: RoutePlanRepository,
    private val sessionStateProvider: () -> SessionState
) : ViewModel() {
    private val _uiState = MutableStateFlow<RoutePlanDetailUiState>(RoutePlanDetailUiState.Loading)
    val uiState: StateFlow<RoutePlanDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = RoutePlanDetailUiState.Loading
            when (val sessionState = sessionStateProvider()) {
                is SessionState.Authenticated -> {
                    val userId = sessionState.userId
                    if (userId == null) {
                        _uiState.value = RoutePlanDetailUiState.Error("Sessao autenticada sem identificador remoto.")
                        return@launch
                    }

                    val detail = runCatching {
                        routePlanRepository.fetchRoutePlanDetail(routePlanId, sessionState)
                    }.getOrElse { error ->
                        _uiState.value = RoutePlanDetailUiState.Error(
                            error.message ?: "Nao foi possivel abrir o percurso."
                        )
                        return@launch
                    }

                    _uiState.value = if (detail == null) {
                        RoutePlanDetailUiState.Error("Percurso nao encontrado.")
                    } else {
                        RoutePlanDetailUiState.Success(detail)
                    }
                }

                is SessionState.Guest -> {
                    _uiState.value = RoutePlanDetailUiState.GuestRestricted
                }

                SessionState.Loading,
                SessionState.LoggedOut -> {
                    _uiState.value = RoutePlanDetailUiState.Error("Sessao indisponivel.")
                }
            }
        }
    }

    companion object {
        fun factory(context: Context, routePlanId: Int): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    val authRepository = AppContainer.provideAuthRepository(appContext)
                    return RoutePlanDetailViewModel(
                        routePlanId = routePlanId,
                        routePlanRepository = AppContainer.provideRoutePlanRepository(appContext),
                        sessionStateProvider = authRepository::currentSessionState
                    ) as T
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlanDetailScreen(
    routePlanId: Int,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: RoutePlanDetailViewModel = viewModel(
        factory = RoutePlanDetailViewModel.factory(context.applicationContext, routePlanId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Detalhe do percurso",
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
                    containerColor = GeodouroWhite
                )
            )
        },
        containerColor = GeodouroWhite
    ) { padding ->
        when (val state = uiState) {
            RoutePlanDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is RoutePlanDetailUiState.Error -> {
                RoutePlanEmptyState(
                    title = "Nao foi possivel abrir o percurso.",
                    message = state.message,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            RoutePlanDetailUiState.GuestRestricted -> {
                RoutePlanEmptyState(
                    title = "Percurso indisponivel em modo convidado.",
                    message = "Entra com a tua conta para veres os detalhes e o circuito do percurso.",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            is RoutePlanDetailUiState.Success -> {
                RoutePlanDetailContent(routePlan = state.routePlan, padding = padding)
            }
        }
    }
}

@Composable
private fun RoutePlanDetailContent(
    routePlan: RoutePlanRepository.RoutePlanDetail,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(GeodouroWhite),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = routePlan.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = GeodouroTextPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    routePlan.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = GeodouroTextSecondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoutePlanMetaChip("${routePlan.stopCount} paragens")
                        RoutePlanMetaChip(
                            if (routePlan.startLabel.isNullOrBlank()) {
                                "Inicio e fim na primeira paragem"
                            } else {
                                routePlan.startLabel
                            }
                        )
                    }
                }
            }
        }

        item { RouteStartCard(routePlan) }
        item { RouteMapCard(routePlan) }

        item {
            Text(
                text = "Paragens por ordem",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GeodouroTextPrimary
            )
        }

        items(routePlan.stops, key = { it.routePlanPointId }) { stop ->
            RouteStopCard(stop)
        }
    }
}

@Composable
private fun RouteStartCard(routePlan: RoutePlanRepository.RoutePlanDetail) {
    val hasCustomStart = routePlan.startLatitude != null && routePlan.startLongitude != null
    Card(
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = GeodouroLightBg) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = GeodouroBrandGreen,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(22.dp)
                )
            }
            Column {
                Text(
                    text = if (hasCustomStart) "Ponto de partida" else "Inicio e fim do circuito",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = GeodouroTextPrimary
                )
                Text(
                    text = when {
                        !routePlan.startLabel.isNullOrBlank() -> routePlan.startLabel
                        routePlan.stops.isNotEmpty() -> routePlan.stops.first().title
                        else -> "Sem ponto de partida registado"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = GeodouroTextSecondary
                )
            }
        }
    }
}

@Composable
private fun RouteMapCard(routePlan: RoutePlanRepository.RoutePlanDetail) {
    val points = buildVisualPoints(routePlan)
    Card(
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Map, contentDescription = null, tint = GeodouroBrandGreen)
                Text(
                    text = "Mapa do percurso",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = GeodouroTextPrimary
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.25f),
                color = GeodouroLightBg,
                shape = RoundedCornerShape(16.dp)
            ) {
                if (points.size < 2) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Sem coordenadas suficientes para desenhar o circuito.",
                            color = GeodouroTextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val padding = 28f
                        val width = size.width
                        val height = size.height

                        drawRect(
                            color = Color.White.copy(alpha = 0.4f),
                            size = Size(width, height)
                        )

                        repeat(4) { index ->
                            val ratio = (index + 1) / 5f
                            drawLine(
                                color = GeodouroBrandGreen.copy(alpha = 0.08f),
                                start = Offset(width * ratio, padding),
                                end = Offset(width * ratio, height - padding),
                                strokeWidth = 2f
                            )
                            drawLine(
                                color = GeodouroBrandGreen.copy(alpha = 0.08f),
                                start = Offset(padding, height * ratio),
                                end = Offset(width - padding, height * ratio),
                                strokeWidth = 2f
                            )
                        }

                        val latitudes = points.map { it.latitude }
                        val longitudes = points.map { it.longitude }
                        val minLat = latitudes.minOrNull() ?: 0.0
                        val maxLat = latitudes.maxOrNull() ?: 0.0
                        val minLon = longitudes.minOrNull() ?: 0.0
                        val maxLon = longitudes.maxOrNull() ?: 0.0
                        val latRange = max(0.0001, maxLat - minLat)
                        val lonRange = max(0.0001, maxLon - minLon)

                        fun project(latitude: Double, longitude: Double): Offset {
                            val x = padding + (((longitude - minLon) / lonRange).toFloat() * (width - (padding * 2)))
                            val y = (height - padding) - (((latitude - minLat) / latRange).toFloat() * (height - (padding * 2)))
                            return Offset(x, y)
                        }

                        val projectedPoints = points.map { project(it.latitude, it.longitude) }
                        val path = Path().apply {
                            projectedPoints.firstOrNull()?.let { moveTo(it.x, it.y) }
                            projectedPoints.drop(1).forEach { point ->
                                lineTo(point.x, point.y)
                            }
                        }

                        drawPath(
                            path = path,
                            color = GeodouroBrandGreen,
                            style = Stroke(width = 8f, cap = StrokeCap.Round)
                        )

                        projectedPoints.forEachIndexed { index, point ->
                            val isStart = index == 0
                            drawCircle(
                                color = if (isStart) GeodouroBrandGreen else GeodouroGreen,
                                radius = if (isStart) 14f else 11f,
                                center = point
                            )
                            drawCircle(
                                color = Color.White,
                                radius = if (isStart) 6f else 4f,
                                center = point
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class VisualRoutePoint(
    val latitude: Double,
    val longitude: Double
)

private fun buildVisualPoints(routePlan: RoutePlanRepository.RoutePlanDetail): List<VisualRoutePoint> {
    val backendGeometry = routePlan.routeGeometry.map { point ->
        VisualRoutePoint(point.latitude, point.longitude)
    }
    if (backendGeometry.size >= 2) {
        return backendGeometry
    }

    val stops = routePlan.stops.mapNotNull { stop ->
        if (stop.latitude != null && stop.longitude != null) {
            VisualRoutePoint(stop.latitude, stop.longitude)
        } else {
            null
        }
    }

    val startPoint = if (routePlan.startLatitude != null && routePlan.startLongitude != null) {
        VisualRoutePoint(routePlan.startLatitude, routePlan.startLongitude)
    } else {
        stops.firstOrNull()
    } ?: return emptyList()

    val points = mutableListOf<VisualRoutePoint>()
    points += startPoint
    points += stops
    if (points.lastOrNull() != startPoint) {
        points += startPoint
    }
    return points
}

@Composable
private fun RouteStopCard(stop: RoutePlanRepository.RoutePlanStop) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = GeodouroBrandGreen.copy(alpha = 0.12f)) {
                Text(
                    text = stop.visitOrder.toString(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = GeodouroBrandGreen,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stop.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = GeodouroTextPrimary
                )
                stop.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GeodouroTextSecondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoutePlanMetaChip(stop.targetType.asUiLabel())
                    if (stop.latitude != null && stop.longitude != null) {
                        RoutePlanMetaChip("GPS")
                    }
                }
            }
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = GeodouroGreen
            )
        }
    }
}

private fun String.asUiLabel(): String {
    return when (lowercase()) {
        "observation" -> "Observacao"
        "publication" -> "Publicacao"
        "species" -> "Especie"
        else -> replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
