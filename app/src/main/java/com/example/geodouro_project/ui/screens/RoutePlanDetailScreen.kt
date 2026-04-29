package com.example.geodouro_project.ui.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(GeodouroBg)
    ) {
        RouteMapCard(
            routePlan = routePlan,
            modifier = Modifier.fillMaxSize()
        )

        RouteStopsOverlay(
            routePlan = routePlan,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun RouteStopsOverlay(
    routePlan: RoutePlanRepository.RoutePlanDetail,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = GeodouroWhite.copy(alpha = 0.94f),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Paragens",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = GeodouroTextPrimary
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                routePlan.stops.forEach { stop ->
                    RouteStopMiniCard(stop)
                }
            }
        }
    }
}

@Composable
private fun RouteMapCard(
    routePlan: RoutePlanRepository.RoutePlanDetail,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val routePoints = remember(routePlan) { buildRouteGeometryPoints(routePlan) }
    val stopPoints = remember(routePlan) { buildStopRoutePoints(routePlan) }
    var selectedStop by remember(routePlan) { mutableStateOf<RoutePlanRepository.RoutePlanStop?>(null) }
    var locationPermissionGranted by remember { mutableStateOf(hasFineLocationPermission(context)) }
    var recenterToUserTrigger by remember { mutableStateOf(0) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationPermissionGranted = granted
        if (granted) {
            recenterToUserTrigger += 1
        }
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted) {
            recenterToUserTrigger += 1
            while (true) {
                delay(15_000)
                recenterToUserTrigger += 1
            }
        }
    }

    Box(modifier = modifier) {
        InAppRouteMap(
            routePoints = routePoints,
            stopPoints = stopPoints,
            onStopClick = { selectedStop = it },
            showUserLocation = locationPermissionGranted,
            recenterToUserTrigger = recenterToUserTrigger,
            modifier = Modifier.fillMaxSize()
        )

        selectedStop?.let { stop ->
            RouteStopPreviewCard(
                stop = stop,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            )
        }
    }
}

@Composable
private fun InAppRouteMap(
    routePoints: List<VisualRoutePoint>,
    stopPoints: List<VisualRoutePoint>,
    onStopClick: (RoutePlanRepository.RoutePlanStop) -> Unit,
    showUserLocation: Boolean,
    recenterToUserTrigger: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    var hasFittedRouteBounds by remember(routePoints, stopPoints) { mutableStateOf(false) }
    var lastHandledRecenterTrigger by remember { mutableStateOf(-1) }

    DisposableEffect(context) {
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 4.0
            maxZoomLevel = 20.0
            controller.setZoom(15.0)
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.overlays.clear()

            val geoPoints = routePoints.map { GeoPoint(it.latitude, it.longitude) }
            if (geoPoints.isNotEmpty()) {
                val polyline = Polyline().apply {
                    outlinePaint.color = android.graphics.Color.parseColor("#2E7D32")
                    outlinePaint.strokeWidth = 9f
                    setPoints(geoPoints)
                }
                view.overlays.add(polyline)
            }

            val markerOverlay = FolderOverlay()
            stopPoints.forEach { point ->
                val marker = Marker(view).apply {
                    position = GeoPoint(point.latitude, point.longitude)
                    title = point.stop?.title ?: point.label?.let { "Paragem $it" } ?: "Paragem"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    point.stop?.let { stop ->
                        setOnMarkerClickListener { _, _ ->
                            onStopClick(stop)
                            true
                        }
                    }
                }
                markerOverlay.add(marker)
            }
            view.overlays.add(markerOverlay)

            if (showUserLocation) {
                val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), view).apply {
                    enableMyLocation()
                    disableFollowLocation()
                }
                view.overlays.add(locationOverlay)
            }

            val boundsPoints = buildList {
                addAll(geoPoints)
                addAll(stopPoints.map { GeoPoint(it.latitude, it.longitude) })
            }
            if (boundsPoints.isNotEmpty() && !hasFittedRouteBounds) {
                val bounds = BoundingBox.fromGeoPointsSafe(boundsPoints)
                view.post {
                    view.zoomToBoundingBox(bounds, true, 96)
                    hasFittedRouteBounds = true
                }
            }

            if (showUserLocation && recenterToUserTrigger != lastHandledRecenterTrigger) {
                val userPoint = view.overlays
                    .filterIsInstance<MyLocationNewOverlay>()
                    .firstOrNull()
                    ?.myLocation

                if (userPoint != null) {
                    view.controller.animateTo(userPoint)
                    if (view.zoomLevelDouble < 16.5) {
                        view.controller.setZoom(16.5)
                    }
                    lastHandledRecenterTrigger = recenterToUserTrigger
                }
            }

            view.invalidate()
        }
    )
}

@Composable
private fun RouteStopMiniCard(stop: RoutePlanRepository.RoutePlanStop) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = GeodouroLightBg,
        modifier = Modifier.width(170.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${stop.visitOrder}. ${stop.title}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = GeodouroTextPrimary,
                maxLines = 2
            )
            stop.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroTextSecondary,
                    maxLines = 2
                )
            }
            Text(
                text = stop.targetType.asUiLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = GeodouroBrandGreen
            )
        }
    }
}

private data class VisualRoutePoint(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null,
    val stop: RoutePlanRepository.RoutePlanStop? = null
)

private fun buildStopRoutePoints(routePlan: RoutePlanRepository.RoutePlanDetail): List<VisualRoutePoint> {
    return routePlan.stops.mapNotNull { stop ->
        if (stop.latitude != null && stop.longitude != null) {
            VisualRoutePoint(
                latitude = stop.latitude,
                longitude = stop.longitude,
                label = stop.visitOrder.toString(),
                stop = stop
            )
        } else {
            null
        }
    }
}

private fun buildRouteGeometryPoints(routePlan: RoutePlanRepository.RoutePlanDetail): List<VisualRoutePoint> {
    val routeGeometry = routePlan.routeGeometry.map {
        VisualRoutePoint(latitude = it.latitude, longitude = it.longitude)
    }
    return if (routeGeometry.isNotEmpty()) {
        routeGeometry
    } else {
        buildStopRoutePoints(routePlan)
    }
}

@Composable
private fun RouteStopPreviewCard(
    stop: RoutePlanRepository.RoutePlanStop,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = GeodouroWhite.copy(alpha = 0.97f),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stop.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = stop.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(GeodouroLightBg),
                    contentScale = ContentScale.Crop
                )
            }
            Text(
                text = "${stop.visitOrder}. ${stop.title}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = GeodouroTextPrimary
            )
            stop.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroTextSecondary
                )
            }
        }
    }
}

private fun hasFineLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
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
