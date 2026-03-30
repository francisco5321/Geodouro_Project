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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.geodouro_project.R
import com.example.geodouro_project.ai.MobileNetV3Classifier
import com.example.geodouro_project.domain.model.LocalInferenceResult
import com.example.geodouro_project.domain.model.LocalPredictionCandidate
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.example.geodouro_project.ui.theme.GeodouroGreen
import com.example.geodouro_project.ui.theme.GeodouroGrey
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
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
    var locationLabel by rememberSaveable { mutableStateOf("A obter localizacao GPS...") }

    fun refreshLocation() {
        if (!hasLocationPermission(context)) {
            currentLatitude = null
            currentLongitude = null
            locationLabel = "Sem permissao de localizacao"
            return
        }

        locationLabel = "A obter localizacao GPS..."
        requestCurrentLocation(context) { location ->
            if (location != null) {
                currentLatitude = location.latitude
                currentLongitude = location.longitude
                locationLabel = "GPS: %.6f, %.6f".format(location.latitude, location.longitude)
            } else {
                currentLatitude = null
                currentLongitude = null
                locationLabel = "Localizacao indisponivel"
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_GALLERY_IMAGES)
    ) { uris ->
        if (uris.isEmpty()) {
            scope.launch {
                snackbarHostState.showSnackbar("Nenhuma imagem selecionada na galeria.")
            }
            return@rememberLauncherForActivityResult
        }

        val newUris = uris.map { it.toString() }
            .filter { it.isNotBlank() }
            .filterNot { capturedImageUris.contains(it) }

        capturedImageUris.addAll(newUris)
        refreshLocation()
        scope.launch {
            snackbarHostState.showSnackbar(
                "${newUris.size} imagem(ns) adicionada(s) da galeria."
            )
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) {
            scope.launch {
                snackbarHostState.showSnackbar("Captura cancelada.")
            }
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            runCatching {
                val imageUri = saveCapturedBitmap(context, bitmap)
                capturedImageUris.add(imageUri)
                snackbarHostState.showSnackbar(
                    "Imagem ${capturedImageUris.size} capturada."
                )
                refreshLocation()
            }.onFailure {
                snackbarHostState.showSnackbar(
                    "Falha ao guardar captura: ${it.message ?: "erro desconhecido"}"
                )
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Permissao de camera negada.")
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            refreshLocation()
        } else {
            locationLabel = "Sem permissao de localizacao"
            scope.launch {
                snackbarHostState.showSnackbar("Permissao de localizacao negada.")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) {
            refreshLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
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
        }
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GeodouroWhite)
                .verticalScroll(rememberScrollState())
                .padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(GeodouroLightBg)
                    .alpha(0.3f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = GeodouroGrey.copy(alpha = 0.3f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = GeodouroGrey,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = locationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroGrey
                )
            }

                OutlinedButton(
                    onClick = { refreshLocation() },
                    enabled = !isProcessing,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text("Atualizar localizacao")
                }

                Text(
                    text = if (classifier.isModelAvailable()) {
                        "Modelo ${MobileNetV3Classifier.MODEL_DISPLAY_NAME} pronto"
                    } else {
                        classifier.getModelLoadDiagnostic()
                            ?: "Modelo ainda nao encontrado (assets/${MobileNetV3Classifier.DEFAULT_MODEL_FILE})"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroTextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = if (isProcessing) "A identificar..." else "Escolhe imagens da camara ou galeria",
                    style = MaterialTheme.typography.bodyLarge,
                    color = GeodouroTextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "Imagens selecionadas: ${capturedImageUris.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GeodouroTextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            if (isProcessing) {
                                return@FloatingActionButton
                            }

                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED

                            if (granted) {
                                cameraLauncher.launch(null)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp),
                        containerColor = GeodouroGreen,
                        shape = CircleShape
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(42.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Capturar foto",
                                modifier = Modifier.size(42.dp),
                                tint = Color.White
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (!isProcessing) {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Escolher da galeria",
                                tint = GeodouroGreen,
                                modifier = Modifier.size(30.dp)
                            )
                            Text("Galeria", color = GeodouroGreen)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (isProcessing || capturedImageUris.isEmpty()) {
                            return@Button
                        }

                        scope.launch {
                            isProcessing = true

                            runCatching {
                                val resolvedLocation = if (
                                    currentLatitude != null && currentLongitude != null
                                ) {
                                    currentLatitude to currentLongitude
                                } else {
                                    withTimeoutOrNull(2_000) {
                                        requestCurrentLocationSuspend(context)
                                    }
                                }

                                if (resolvedLocation != null) {
                                    currentLatitude = resolvedLocation.first
                                    currentLongitude = resolvedLocation.second
                                    locationLabel = "GPS: %.6f, %.6f".format(
                                        resolvedLocation.first,
                                        resolvedLocation.second
                                    )
                                }

                                if (capturedImageUris.size >= 2) {
                                    onIdentifyMultipleClick(
                                        capturedImageUris.toList(),
                                        currentLatitude,
                                        currentLongitude
                                    )
                                } else {
                                    val imageUri = capturedImageUris.first()
                                    val bitmap = decodeCapturedBitmap(context, imageUri)
                                        ?: throw IllegalStateException("Nao foi possivel ler a imagem selecionada")
                                    val prediction = classifier.classify(bitmap)

                                    if (!prediction.fromModel) {
                                        val diagnostic = classifier.getModelLoadDiagnostic()
                                        snackbarHostState.showSnackbar(
                                            if (diagnostic.isNullOrBlank()) {
                                                "Modelo ${MobileNetV3Classifier.MODEL_DISPLAY_NAME} ainda indisponivel. Resultado de fallback aplicado."
                                            } else {
                                                "Modelo ${MobileNetV3Classifier.MODEL_DISPLAY_NAME} indisponivel: $diagnostic"
                                            }
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
                                                LocalPredictionCandidate(
                                                    species = candidate.label,
                                                    confidence = candidate.confidence
                                                )
                                            }
                                        )
                                    )
                                }
                            }.onFailure {
                                snackbarHostState.showSnackbar(
                                    "Falha na identificacao local: ${it.message ?: "erro desconhecido"}"
                                )
                            }

                            isProcessing = false
                        }
                    },
                    enabled = !isProcessing && capturedImageUris.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val label = if (capturedImageUris.size >= 2) {
                        "Analisar ${capturedImageUris.size} imagens"
                    } else {
                        "Analisar 1 imagem"
                    }
                    Text(label)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { capturedImageUris.clear() },
                    enabled = !isProcessing && capturedImageUris.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Limpar capturas")
                }

                Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = GeodouroLightBg
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = GeodouroGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column {
                        Text(
                            text = "Recomendacao",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = GeodouroTextPrimary
                        )
                        Text(
                            text = "Podes combinar fotos da camara e da galeria para usar o modo multi-imagem com consenso.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GeodouroTextSecondary
                        )
                    }
                }
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun requestCurrentLocation(context: Context, onLocationResolved: (Location?) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    runCatching {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    onLocationResolved(location)
                } else {
                    resolveLastKnownLocation(context, onLocationResolved)
                }
            }
            .addOnFailureListener {
                resolveLastKnownLocation(context, onLocationResolved)
            }
    }.onFailure {
        resolveLastKnownLocation(context, onLocationResolved)
    }
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
                if (lastLocation != null) {
                    onLocationResolved(lastLocation)
                } else {
                    resolveLocationManagerLastKnownLocation(context, onLocationResolved)
                }
            }
            .addOnFailureListener {
                resolveLocationManagerLastKnownLocation(context, onLocationResolved)
            }
    }.onFailure {
        resolveLocationManagerLastKnownLocation(context, onLocationResolved)
    }
}

private fun resolveLocationManagerLastKnownLocation(
    context: Context,
    onLocationResolved: (Location?) -> Unit
) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: run {
            onLocationResolved(null)
            return
        }

    val provider = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    if (provider == null) {
        onLocationResolved(
            sequenceOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { knownProvider ->
                    runCatching { locationManager.getLastKnownLocation(knownProvider) }.getOrNull()
                }
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
                    .mapNotNull { knownProvider ->
                        runCatching { locationManager.getLastKnownLocation(knownProvider) }.getOrNull()
                    }
                    .maxByOrNull { it.time }
            )
        }
    }.onFailure {
        onLocationResolved(
            sequenceOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { knownProvider ->
                    runCatching { locationManager.getLastKnownLocation(knownProvider) }.getOrNull()
                }
                .maxByOrNull { it.time }
        )
    }
}

private fun decodeCapturedBitmap(context: Context, imageUri: String): Bitmap? {
    val parsedUri = Uri.parse(imageUri)
    return runCatching {
        context.contentResolver.openInputStream(parsedUri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    }.getOrNull()
}

private fun saveCapturedBitmap(context: Context, bitmap: Bitmap): String {
    val capturesDir = File(context.cacheDir, "captures").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    val imageFile = File(capturesDir, "capture_${System.currentTimeMillis()}.jpg")
    FileOutputStream(imageFile).use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
    }

    return Uri.fromFile(imageFile).toString()
}

private const val MAX_GALLERY_IMAGES = 10
