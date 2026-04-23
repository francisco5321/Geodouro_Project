package com.example.geodouro_project.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.geodouro_project.core.location.LocationResolver
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun RoutePlanDetailScreen(
    refreshTrigger: Int = 0,
    routePlanId: Int,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: RoutePlanDetailViewModel = viewModel(
        factory = RoutePlanDetailViewModel.factory(context.applicationContext, routePlanId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading = uiState is RoutePlanDetailUiState.Loading
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = { viewModel.refresh() }
    )

    LaunchedEffect(refreshTrigger, routePlanId) {
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
                        "Detalhe do percurso",
                        style = MaterialTheme.typography.titleLarge,
                        color = GeodouroBrandGreen,
                        fontWeight = FontWeight.Bold
                    )
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GeodouroBg)
                .pullRefresh(pullRefreshState)
        ) {
            when (val state = uiState) {
                RoutePlanDetailUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is RoutePlanDetailUiState.Error -> {
                    RoutePlanEmptyState(
                        title = "Nao foi possivel abrir o percurso.",
                        message = state.message,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                RoutePlanDetailUiState.GuestRestricted -> {
                    RoutePlanEmptyState(
                        title = "Percurso indisponivel em modo convidado.",
                        message = "Entra com a tua conta para veres os detalhes e o circuito do percurso.",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is RoutePlanDetailUiState.Success -> {
                    RoutePlanDetailContent(routePlan = state.routePlan, padding = PaddingValues(0.dp))
                }
            }

            PullRefreshIndicator(
                refreshing = isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = GeodouroBg,
                contentColor = GeodouroBrandGreen
            )
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
            .background(GeodouroBg),
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
                        RoutePlanMetaChip("Partida: localizacao atual")
                    }
                }
            }
        }

        item { RouteStartCard() }
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
private fun RouteStartCard() {
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
                    text = "Ponto de partida",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = GeodouroTextPrimary
                )
                Text(
                    text = "A tua localizacao atual, ao abrir o percurso no Google Maps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GeodouroTextSecondary
                )
            }
        }
    }
}

@Composable
private fun RouteMapCard(routePlan: RoutePlanRepository.RoutePlanDetail) {
    val points = buildGoogleMapsPoints(routePlan)
    val context = LocalContext.current
    val appContext = context.applicationContext
    val locationResolver = remember(appContext) { LocationResolver(appContext) }
    val coroutineScope = rememberCoroutineScope()
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        coroutineScope.launch {
            val originCoordinates = if (granted) {
                locationResolver.getCurrentCoordinates()
            } else {
                null
            }
            openDriveToRouteStartInGoogleMaps(context, points, originCoordinates)
        }
    }
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
                    .padding(top = 4.dp),
                color = GeodouroLightBg,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (points.isEmpty()) {
                            "Adiciona pelo menos uma paragem com coordenadas para abrir no Google Maps."
                        } else {
                            "Primeiro navega de carro ate ao inicio. Depois abre o circuito a pe, passando pelas paragens e regressando ao primeiro ponto."
                        },
                        color = GeodouroTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            if (locationResolver.hasLocationPermission()) {
                                coroutineScope.launch {
                                    openDriveToRouteStartInGoogleMaps(
                                        context = context,
                                        points = points,
                                        originCoordinates = locationResolver.getCurrentCoordinates()
                                    )
                                }
                            } else {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        enabled = points.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = GeodouroBrandGreen),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Ir ate ao inicio de carro")
                    }
                    Button(
                        onClick = { openWalkingRouteInGoogleMaps(context, points) },
                        enabled = points.size >= 2,
                        colors = ButtonDefaults.buttonColors(containerColor = GeodouroGreen),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Percurso a pe")
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

private fun openDriveToRouteStartInGoogleMaps(
    context: Context,
    points: List<VisualRoutePoint>,
    originCoordinates: Pair<Double, Double>?
) {
    if (points.isEmpty()) return

    val origin = originCoordinates?.let { (latitude, longitude) ->
        "$latitude,$longitude"
    } ?: "Current Location"
    val destination = points.first().asMapsCoordinate()

    val uriBuilder = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
        .appendQueryParameter("api", "1")
        .appendQueryParameter("origin", origin)
        .appendQueryParameter("destination", destination)
        .appendQueryParameter("travelmode", "driving")

    openMapsIntent(context, uriBuilder.build())
}

private fun openWalkingRouteInGoogleMaps(context: Context, points: List<VisualRoutePoint>) {
    if (points.size < 2) return

    val origin = points.first().asMapsCoordinate()
    val destination = points.first().asMapsCoordinate()
    val waypoints = points
        .drop(1)
        .take(23)
        .joinToString("|") { it.asMapsCoordinate() }

    val uriBuilder = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
        .appendQueryParameter("api", "1")
        .appendQueryParameter("origin", origin)
        .appendQueryParameter("destination", destination)
        .appendQueryParameter("travelmode", "walking")

    if (waypoints.isNotBlank()) {
        uriBuilder.appendQueryParameter("waypoints", waypoints)
    }

    openMapsIntent(context, uriBuilder.build())
}

private fun openMapsIntent(context: Context, uri: Uri) {
    val mapsIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
    }

    val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
    runCatching {
        context.startActivity(mapsIntent)
    }.getOrElse {
        context.startActivity(fallbackIntent)
    }
}

private fun VisualRoutePoint.asMapsCoordinate(): String = "$latitude,$longitude"

private fun buildGoogleMapsPoints(routePlan: RoutePlanRepository.RoutePlanDetail): List<VisualRoutePoint> {
    return routePlan.stops.mapNotNull { stop ->
        if (stop.latitude != null && stop.longitude != null) {
            VisualRoutePoint(stop.latitude, stop.longitude)
        } else {
            null
        }
    }
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
