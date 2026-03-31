package com.example.geodouro_project.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.geodouro_project.R
import com.example.geodouro_project.ai.MobileNetV3Classifier
import com.example.geodouro_project.domain.model.LocalInferenceResult
import com.example.geodouro_project.domain.model.LocalPredictionCandidate
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.example.geodouro_project.ui.theme.GeodouroGreen
import com.example.geodouro_project.ui.theme.GeodouroBrandGreen
import com.example.geodouro_project.ui.theme.GeodouroGrey
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
import com.example.geodouro_project.ui.theme.geodouroLoadingIndicatorColor
import com.example.geodouro_project.ui.theme.geodouroOutlinedBorderColor
import com.example.geodouro_project.ui.theme.geodouroOutlinedButtonColors
import com.example.geodouro_project.ui.theme.geodouroPrimaryButtonColors
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentifyScreen(
    onIdentifyClick: (LocalInferenceResult) -> Unit,
    onIdentifyMultipleClick: (List<String>, Double?, Double?) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val classifier = remember { MobileNetV3Classifier(context.applicationContext) }
    val snackbarHostState = remember { SnackbarHostState() }
    var isProcessing by rememberSaveable { mutableStateOf(false) }
    val capturedImageUris = remember { mutableStateListOf<String>() }
    var currentLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var currentLongitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var locationLabel by rememberSaveable { mutableStateOf("A obter localização GPS...") }

    // Animate FAB scale when processing
    val cameraFabScale by animateFloatAsState(
        targetValue = if (isProcessing) 0.95f else 1f,
        animationSpec = tween(200),
        label = "cameraFabScale"
    )

    fun refreshLocation() {
        if (!hasLocationPermission(context)) {
            currentLatitude = null
            currentLongitude = null
            locationLabel = "Sem permissão de localização"
            return
        }
        locationLabel = "A obter localização GPS..."
        requestCurrentLocation(context) { location ->
            if (location != null) {
                currentLatitude = location.latitude
                currentLongitude = location.longitude
                locationLabel = "%.6f, %.6f".format(location.latitude, location.longitude)
            } else {
                currentLatitude = null
                currentLongitude = null
                locationLabel = "Localização indisponível"
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_GALLERY_IMAGES)
    ) { uris ->
        if (uris.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("Nenhuma imagem selecionada na galeria.") }
            return@rememberLauncherForActivityResult
        }
        val newUris = uris.map { it.toString() }
            .filter { it.isNotBlank() }
            .filterNot { capturedImageUris.contains(it) }
        capturedImageUris.addAll(newUris)
        refreshLocation()
        scope.launch { snackbarHostState.showSnackbar("${newUris.size} imagem(ns) adicionada(s) da galeria.") }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) {
            scope.launch { snackbarHostState.showSnackbar("Captura cancelada.") }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            runCatching {
                val imageUri = saveCapturedBitmap(context, bitmap)
                capturedImageUris.add(imageUri)
                snackbarHostState.showSnackbar("Imagem ${capturedImageUris.size} capturada.")
                refreshLocation()
            }.onFailure {
                snackbarHostState.showSnackbar("Falha ao guardar captura: ${it.message ?: "erro desconhecido"}")
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(null)
        else scope.launch { snackbarHostState.showSnackbar("Permissão de câmara negada.") }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) refreshLocation()
        else {
            locationLabel = "Sem permissão de localização"
            scope.launch { snackbarHostState.showSnackbar("Permissão de localização negada.") }
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) refreshLocation()
        else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.logo_s_fundo),
                        contentDescription = "Geodouro",
                        modifier = Modifier.height(80.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GeodouroWhite
                )
            )
        },
        containerColor = GeodouroWhite
    ) { padding ->

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GeodouroWhite)
                .verticalScroll(rememberScrollState())
        ) {

            // ── GPS pill ──────────────────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 20.dp, bottom = 4.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = GeodouroLightBg,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(GeodouroBrandGreen.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = GeodouroBrandGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Localização GPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = GeodouroTextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = locationLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = GeodouroTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(
                        onClick = { refreshLocation() },
                        enabled = !isProcessing,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar localização",
                            tint = if (!isProcessing) GeodouroBrandGreen else GeodouroGrey,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ── Model status chip ─────────────────────────────────────────────
            val modelReady = classifier.isModelAvailable()
            Surface(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                shape = RoundedCornerShape(50),
                color = if (modelReady)
                    GeodouroBrandGreen.copy(alpha = 0.10f)
                else
                    GeodouroGrey.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (modelReady) GeodouroBrandGreen else GeodouroGrey)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = if (modelReady)
                            "Modelo ${MobileNetV3Classifier.MODEL_DISPLAY_NAME} pronto"
                        else
                            classifier.getModelLoadDiagnostic()
                                ?: "Modelo indisponível (assets/${MobileNetV3Classifier.DEFAULT_MODEL_FILE})",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (modelReady) GeodouroBrandGreen else GeodouroGrey,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Hero capture area ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                GeodouroLightBg,
                                GeodouroLightBg.copy(alpha = 0.6f)
                            )
                        )
                    )
                    .padding(vertical = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    // Headline
                    Text(
                        text = if (isProcessing) "A identificar…" else "Identificar espécie",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GeodouroTextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (isProcessing)
                            "Por favor aguarda"
                        else
                            "Tira uma foto ou escolhe da galeria",
                        style = MaterialTheme.typography.bodySmall,
                        color = GeodouroTextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
                        textAlign = TextAlign.Center
                    )

                    // Primary camera FAB
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .scale(cameraFabScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        GeodouroBrandGreen,
                                        GeodouroBrandGreen.copy(alpha = 0.82f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.FloatingActionButton(
                            onClick = {
                                if (isProcessing) return@FloatingActionButton
                                val granted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) cameraLauncher.launch(null)
                                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            modifier = Modifier.size(136.dp),
                            containerColor = Color.Transparent,
                            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                            shape = CircleShape
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = Color.White,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Capturar foto",
                                    modifier = Modifier.size(54.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Secondary gallery button
                    OutlinedButton(
                        onClick = {
                            if (!isProcessing) galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.5.dp, GeodouroGreen.copy(alpha = if (!isProcessing) 1f else 0.35f)),
                        colors = geodouroOutlinedButtonColors(),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = if (!isProcessing) GeodouroGreen else GeodouroGrey,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Escolher da Galeria",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── Image counter badge ───────────────────────────────────────────
            AnimatedVisibility(
                visible = capturedImageUris.isNotEmpty(),
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }
            ) {
                Surface(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = GeodouroBrandGreen.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(GeodouroBrandGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${capturedImageUris.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (capturedImageUris.size == 1)
                                    "imagem pronta para análise"
                                else
                                    "imagens prontas para análise",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeodouroTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        // Inline clear button
                        androidx.compose.material3.TextButton(
                            onClick = { capturedImageUris.clear() },
                            enabled = !isProcessing
                        ) {
                            Text(
                                "Limpar",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (!isProcessing) GeodouroTextSecondary else GeodouroGrey.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Primary analyse button ────────────────────────────────────────
            Button(
                onClick = {
                    if (isProcessing || capturedImageUris.isEmpty()) return@Button
                    scope.launch {
                        isProcessing = true
                        runCatching {
                            val resolvedLocation = if (currentLatitude != null && currentLongitude != null) {
                                currentLatitude to currentLongitude
                            } else {
                                withTimeoutOrNull(2_000) { requestCurrentLocationSuspend(context) }
                            }
                            if (resolvedLocation != null) {
                                currentLatitude = resolvedLocation.first
                                currentLongitude = resolvedLocation.second
                                locationLabel = "%.6f, %.6f".format(resolvedLocation.first, resolvedLocation.second)
                            }
                            if (capturedImageUris.size >= 2) {
                                onIdentifyMultipleClick(capturedImageUris.toList(), currentLatitude, currentLongitude)
                            } else {
                                val imageUri = capturedImageUris.first()
                                val bitmap = decodeCapturedBitmap(context, imageUri)
                                    ?: throw IllegalStateException("Não foi possível ler a imagem selecionada")
                                val prediction = classifier.classify(bitmap)
                                if (!prediction.fromModel) {
                                    val diagnostic = classifier.getModelLoadDiagnostic()
                                    snackbarHostState.showSnackbar(
                                        if (diagnostic.isNullOrBlank())
                                            "Modelo ${MobileNetV3Classifier.MODEL_DISPLAY_NAME} ainda indisponível. Resultado de fallback aplicado."
                                        else
                                            "Modelo ${MobileNetV3Classifier.MODEL_DISPLAY_NAME} indisponível: $diagnostic"
                                    )
                                }
                                onIdentifyClick(
                                    LocalInferenceResult(
                                        imageUri = imageUri,
                                        latitude = currentLatitude,
                                        longitude = currentLongitude,
                                        predictedSpecies = prediction.label,
                                        confidence = prediction.confidence,
                                        candidatePredictions = prediction.candidates.map { candidate ->
                                            LocalPredictionCandidate(species = candidate.label, confidence = candidate.confidence)
                                        }
                                    )
                                )
                            }
                        }.onFailure {
                            snackbarHostState.showSnackbar("Falha na identificação local: ${it.message ?: "erro desconhecido"}")
                        }
                        isProcessing = false
                    }
                },
                enabled = !isProcessing && capturedImageUris.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = geodouroPrimaryButtonColors(),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 2.dp
                )
            ) {
                val label = when {
                    isProcessing -> "A analisar…"
                    capturedImageUris.size >= 2 -> "Analisar ${capturedImageUris.size} imagens"
                    else -> "Analisar 1 imagem"
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Tip card ─────────────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = GeodouroLightBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GeodouroBrandGreen.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = GeodouroBrandGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Dica",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = GeodouroBrandGreen,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Combina fotos da câmara e da galeria para ativar o modo multi-imagem com consenso para maior precisão.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GeodouroTextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Helpers (unchanged) ───────────────────────────────────────────────────────

private fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

private fun requestCurrentLocation(context: Context, onLocationResolved: (Location?) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    runCatching {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) onLocationResolved(location)
                else resolveLastKnownLocation(context, onLocationResolved)
            }
            .addOnFailureListener { resolveLastKnownLocation(context, onLocationResolved) }
    }.onFailure { resolveLastKnownLocation(context, onLocationResolved) }
}

private suspend fun requestCurrentLocationSuspend(context: Context): Pair<Double, Double>? {
    return suspendCancellableCoroutine { continuation ->
        requestCurrentLocation(context) { location ->
            if (continuation.isActive) {
                continuation.resume(location?.let { it.latitude to it.longitude })
            }
        }
    }
}

private fun resolveLastKnownLocation(context: Context, onLocationResolved: (Location?) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    runCatching {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { lastLocation ->
                if (lastLocation != null) onLocationResolved(lastLocation)
                else resolveLocationManagerLastKnownLocation(context, onLocationResolved)
            }
            .addOnFailureListener { resolveLocationManagerLastKnownLocation(context, onLocationResolved) }
    }.onFailure { resolveLocationManagerLastKnownLocation(context, onLocationResolved) }
}

private fun resolveLocationManagerLastKnownLocation(
    context: Context,
    onLocationResolved: (Location?) -> Unit
) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: run { onLocationResolved(null); return }

    val provider = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    if (provider == null) {
        onLocationResolved(
            sequenceOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        )
        return
    }

    val executor = ContextCompat.getMainExecutor(context)
    val cancellationSignal = CancellationSignal()

    runCatching {
        locationManager.getCurrentLocation(provider, cancellationSignal, executor) { location ->
            onLocationResolved(
                location ?: sequenceOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
                    .maxByOrNull { it.time }
            )
        }
    }.onFailure {
        onLocationResolved(
            sequenceOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        )
    }
}

private fun decodeCapturedBitmap(context: Context, imageUri: String): Bitmap? {
    val parsedUri = Uri.parse(imageUri)
    return runCatching {
        context.contentResolver.openInputStream(parsedUri)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

private fun saveCapturedBitmap(context: Context, bitmap: Bitmap): String {
    val capturesDir = File(context.cacheDir, "captures").apply { if (!exists()) mkdirs() }
    val imageFile = File(capturesDir, "capture_${System.currentTimeMillis()}.jpg")
    FileOutputStream(imageFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    return Uri.fromFile(imageFile).toString()
}

private const val MAX_GALLERY_IMAGES = 10