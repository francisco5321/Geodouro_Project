package com.example.geodouro_project.ui.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.geodouro_project.data.repository.RoutePlanRepository
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.domain.model.SessionState
import com.example.geodouro_project.ui.theme.GeodouroBg
import com.example.geodouro_project.ui.theme.GeodouroBrandGreen
import com.example.geodouro_project.ui.theme.GeodouroDarkGreen
import com.example.geodouro_project.ui.theme.GeodouroGreen
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
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
import kotlin.math.max

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
                        text = "Percurso",
                        style = MaterialTheme.typography.titleLarge,
                        color = GeodouroWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = GeodouroWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GeodouroDarkGreen
                )
            )
        },
        containerColor = GeodouroDarkGreen
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
                    RoutePlanDetailContent(routePlan = state.routePlan)
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
private fun RoutePlanDetailContent(routePlan: RoutePlanRepository.RoutePlanDetail) {
    var selectedStop by remember(routePlan) { mutableStateOf<RoutePlanRepository.RoutePlanStop?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GeodouroBg)
    ) {
        RouteMapCard(
            routePlan = routePlan,
            selectedStop = selectedStop,
            onStopSelected = { selectedStop = it },
            onPreviewDismiss = { selectedStop = null },
            modifier = Modifier.fillMaxSize()
        )

        RouteObjectivesOverlay(
            routePlan = routePlan,
            onStopClick = { selectedStop = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun RouteObjectivesOverlay(
    routePlan: RoutePlanRepository.RoutePlanDetail,
    onStopClick: (RoutePlanRepository.RoutePlanStop) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleStops = remember(routePlan) { routePlan.stops.take(5) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = GeodouroDarkGreen.copy(alpha = 0.92f),
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Objetivos do percurso",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GeodouroWhite
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                visibleStops.forEach { stop ->
                    RouteObjectiveImageCard(
                        stop = stop,
                        onClick = { onStopClick(stop) }
                    )
                }
            }
            if (visibleStops.isEmpty()) {
                Text(
                    text = "Sem objetivos para mostrar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroWhite.copy(alpha = 0.8f)
                )
            } else if (routePlan.stops.size > visibleStops.size) {
                Text(
                    text = "+${routePlan.stops.size - visibleStops.size} objetivos adicionais",
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroWhite.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun RouteObjectiveImageCard(
    stop: RoutePlanRepository.RoutePlanStop,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(112.dp)
            .clickable(onClick = onClick),
        color = GeodouroBrandGreen.copy(alpha = 0.9f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(GeodouroLightBg)
            ) {
                stop.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = stop.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stop.visitOrder.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = GeodouroBrandGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = stop.title,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                color = GeodouroWhite,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun RouteMapCard(
    routePlan: RoutePlanRepository.RoutePlanDetail,
    selectedStop: RoutePlanRepository.RoutePlanStop?,
    onStopSelected: (RoutePlanRepository.RoutePlanStop) -> Unit,
    onPreviewDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val routePoints = remember(routePlan) { buildRouteGeometryPoints(routePlan) }
    val stopPoints = remember(routePlan) { buildStopRoutePoints(routePlan) }
    var locationPermissionGranted by remember { mutableStateOf(hasFineLocationPermission(context)) }
    var recenterRequest by remember { mutableStateOf(RecenterRequest()) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationPermissionGranted = granted
        if (granted) {
            recenterRequest = recenterRequest.next(immediate = false)
        }
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted) {
            recenterRequest = recenterRequest.next(immediate = false)
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 8.dp
    ) {
        Box {
            InAppRouteMap(
                routePoints = routePoints,
                stopPoints = stopPoints,
                onStopClick = onStopSelected,
                showUserLocation = locationPermissionGranted,
                recenterRequest = recenterRequest,
                modifier = Modifier.fillMaxSize()
            )

            selectedStop?.let { stop ->
                RouteStopPreviewCard(
                    stop = stop,
                    onClose = onPreviewDismiss,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp, start = 16.dp, end = 84.dp)
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                shape = CircleShape,
                color = GeodouroBrandGreen,
                shadowElevation = 6.dp
            ) {
                IconButton(
                    onClick = {
                        if (locationPermissionGranted) {
                            recenterRequest = recenterRequest.next(immediate = true)
                        } else {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Centrar na minha localizacao",
                        tint = GeodouroWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun InAppRouteMap(
    routePoints: List<VisualRoutePoint>,
    stopPoints: List<VisualRoutePoint>,
    onStopClick: (RoutePlanRepository.RoutePlanStop) -> Unit,
    showUserLocation: Boolean,
    recenterRequest: RecenterRequest,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    var hasFittedRouteBounds by remember(routePoints, stopPoints) { mutableStateOf(false) }
    var lastHandledRecenterToken by remember { mutableStateOf(-1) }
    var lastRouteSignature by remember { mutableStateOf<String?>(null) }
    var lastStopSignature by remember { mutableStateOf<String?>(null) }

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
    val locationOverlay = remember(mapView) {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
    }
    val routePolyline = remember(mapView) {
        Polyline().apply {
            outlinePaint.color = android.graphics.Color.parseColor("#3E7A57")
            outlinePaint.strokeWidth = 9f
        }
    }
    val markerOverlay = remember(mapView) { FolderOverlay() }

    DisposableEffect(mapView) {
        if (!mapView.overlays.contains(routePolyline)) {
            mapView.overlays.add(routePolyline)
        }
        if (!mapView.overlays.contains(markerOverlay)) {
            mapView.overlays.add(markerOverlay)
        }
        mapView.onResume()
        onDispose {
            locationOverlay.disableMyLocation()
            locationOverlay.onDetach(mapView)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            val geoPoints = routePoints.map { GeoPoint(it.latitude, it.longitude) }
            val routeSignature = routePoints.joinToString(separator = "|") { point ->
                "${point.latitude},${point.longitude}"
            }
            if (lastRouteSignature != routeSignature) {
                routePolyline.setPoints(geoPoints)
                lastRouteSignature = routeSignature
            }

            val stopSignature = stopPoints.joinToString(separator = "|") { point ->
                "${point.latitude},${point.longitude},${point.label},${point.stop?.routePlanPointId}"
            }
            if (lastStopSignature != stopSignature) {
                markerOverlay.items.clear()
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
                lastStopSignature = stopSignature
            }

            if (showUserLocation) {
                if (!view.overlays.contains(locationOverlay)) {
                    view.overlays.add(locationOverlay)
                }
                locationOverlay.enableMyLocation()
                locationOverlay.disableFollowLocation()
            } else {
                locationOverlay.disableMyLocation()
                view.overlays.remove(locationOverlay)
            }

            val boundsPoints = buildList {
                addAll(geoPoints)
                addAll(stopPoints.map { GeoPoint(it.latitude, it.longitude) })
            }
            if (boundsPoints.isNotEmpty() && !hasFittedRouteBounds) {
                val bounds = BoundingBox.fromGeoPointsSafe(boundsPoints)
                view.post {
                    if (locationOverlay.myLocation == null) {
                        view.zoomToBoundingBox(bounds, true, 96)
                    }
                    hasFittedRouteBounds = true
                }
            }

            if (showUserLocation && recenterRequest.token != lastHandledRecenterToken) {
                val userPoint = locationOverlay.myLocation
                if (userPoint != null) {
                    recenterToUser(
                        view = view,
                        userPoint = userPoint,
                        immediate = recenterRequest.immediate
                    )
                    lastHandledRecenterToken = recenterRequest.token
                } else {
                    locationOverlay.runOnFirstFix {
                        view.post {
                            locationOverlay.myLocation?.let { firstFixPoint ->
                                recenterToUser(
                                    view = view,
                                    userPoint = firstFixPoint,
                                    immediate = recenterRequest.immediate
                                )
                                lastHandledRecenterToken = recenterRequest.token
                            }
                        }
                    }
                }
            }

            view.invalidate()
        }
    )
}

private fun recenterToUser(
    view: MapView,
    userPoint: GeoPoint,
    immediate: Boolean
) {
    val targetZoom = max(view.zoomLevelDouble, 17.0)
    if (immediate) {
        view.controller.setCenter(userPoint)
        if (view.zoomLevelDouble < targetZoom) {
            view.controller.setZoom(targetZoom)
        }
        view.invalidate()
        return
    }

    view.controller.animateTo(userPoint, targetZoom, 650L)
    view.postDelayed(
        {
            view.controller.setCenter(userPoint)
            if (view.zoomLevelDouble < targetZoom) {
                view.controller.setZoom(targetZoom)
            }
            view.invalidate()
        },
        700L
    )
}

private data class RecenterRequest(
    val token: Int = 0,
    val immediate: Boolean = false
) {
    fun next(immediate: Boolean): RecenterRequest = RecenterRequest(
        token = token + 1,
        immediate = immediate
    )
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
    return if (routeGeometry.isNotEmpty()) routeGeometry else buildStopRoutePoints(routePlan)
}

@Composable
private fun RouteStopPreviewCard(
    stop: RoutePlanRepository.RoutePlanStop,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = GeodouroWhite.copy(alpha = 0.98f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 4.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(GeodouroLightBg)
            ) {
                stop.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = stop.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stop.visitOrder.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = GeodouroBrandGreen,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp),
                    color = GeodouroDarkGreen.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "${stop.visitOrder}. paragem",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = GeodouroWhite,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = CircleShape,
                    color = GeodouroWhite.copy(alpha = 0.94f),
                    shadowElevation = 4.dp
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = GeodouroTextPrimary
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoutePlanMetaChip(stop.targetType.asUiLabel())
                    if (stop.latitude != null && stop.longitude != null) {
                        RoutePlanMetaChip("GPS")
                    }
                }

                Text(
                    text = stop.title,
                    style = MaterialTheme.typography.titleLarge,
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

                Text(
                    text = "Toca noutros objetivos ou marcadores do mapa para alternar a observacao em destaque.",
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
private fun RouteStopCard(
    stop: RoutePlanRepository.RoutePlanStop,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(3.dp),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(GeodouroDarkGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stop.visitOrder.toString(),
                    color = GeodouroWhite,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
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

            Surface(
                shape = CircleShape,
                color = GeodouroGreen.copy(alpha = 0.18f)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = GeodouroBrandGreen,
                    modifier = Modifier.padding(12.dp)
                )
            }
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
