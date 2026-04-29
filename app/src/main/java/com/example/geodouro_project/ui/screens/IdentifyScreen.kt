package com.example.geodouro_project.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geodouro_project.domain.model.LocalInferenceResult
import com.example.geodouro_project.domain.model.SessionState
import com.example.geodouro_project.ui.components.GeoFloraHeaderLogo
import com.example.geodouro_project.ui.theme.GeodouroBg
import com.example.geodouro_project.ui.theme.GeodouroBrandGreen
import com.example.geodouro_project.ui.theme.GeodouroCardBg
import com.example.geodouro_project.ui.theme.GeodouroGreen
import com.example.geodouro_project.ui.theme.GeodouroGrey
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
import com.example.geodouro_project.ui.theme.geodouroPrimaryButtonColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentifyScreen(
    onIdentifyClick: (LocalInferenceResult) -> Unit,
    sessionState: SessionState,
    clearCapturesTrigger: Int = 0
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val viewModel: IdentifyViewModel = viewModel(
        factory = IdentifyViewModel.factory(context.applicationContext)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val canCreateObservation = sessionState is SessionState.Authenticated

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.onGallerySelection(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        viewModel.onCameraCaptureResult(saved)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.prepareCameraCapture()?.let { cameraLauncher.launch(it) }
        } else {
            viewModel.onCameraPermissionDenied()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onLocationPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        viewModel.requestInitialLocation()
    }

    LaunchedEffect(clearCapturesTrigger) {
        if (clearCapturesTrigger > 0) {
            viewModel.clearCaptures()
        }
    }

    LaunchedEffect(uiState.shouldRequestLocationPermission) {
        if (uiState.shouldRequestLocationPermission) {
            viewModel.onLocationPermissionRequestHandled()
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { event ->
            when (event) {
                is IdentifyNavigationEvent.SingleResult -> onIdentifyClick(event.result)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                expandedHeight = 48.dp,
                title = {
                    GeoFloraHeaderLogo()
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GeodouroBg
                )
            )
        },
        containerColor = GeodouroBg
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GeodouroBg)
                .verticalScroll(rememberScrollState())
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 20.dp, bottom = 4.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = GeodouroLightBg
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
                            text = "Localizacao GPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = GeodouroTextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = uiState.locationLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = GeodouroTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refreshLocation() },
                        enabled = !uiState.isProcessing,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar localizacao",
                            tint = if (!uiState.isProcessing) GeodouroBrandGreen else GeodouroGrey,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                shape = RoundedCornerShape(50),
                color = if (uiState.modelReady) {
                    GeodouroBrandGreen.copy(alpha = 0.10f)
                } else {
                    GeodouroGrey.copy(alpha = 0.12f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (uiState.modelReady) GeodouroBrandGreen else GeodouroGrey)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = uiState.modelStatusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.modelReady) GeodouroBrandGreen else GeodouroGrey,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = GeodouroCardBg
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (uiState.isProcessing) "A identificar..." else "Identificar espécie",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GeodouroTextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (uiState.isProcessing) {
                            "Por favor aguarda"
                        } else {
                            "Tira uma foto ou escolhe da galeria"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = GeodouroTextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
                        textAlign = TextAlign.Center
                    )

                    FloatingActionButton(
                        onClick = {
                            if (!canCreateObservation) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("É preciso realizar login para fazer observações.")
                                }
                                return@FloatingActionButton
                            }

                            if (uiState.isProcessing) {
                                return@FloatingActionButton
                            }

                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED

                            if (granted) {
                                viewModel.prepareCameraCapture()?.let { cameraLauncher.launch(it) }
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.size(136.dp),
                        containerColor = GeodouroBrandGreen,
                        shape = CircleShape
                    ) {
                        if (uiState.isProcessing) {
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

                    Spacer(modifier = Modifier.height(24.dp))

                    FloatingActionButton(
                        onClick = {
                            if (!canCreateObservation) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("É preciso realizar login para fazer observações.")
                                }
                            } else if (!uiState.isProcessing) {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        },
                        modifier = Modifier.size(86.dp),
                        containerColor = GeodouroWhite,
                        shape = CircleShape
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Escolher da galeria",
                                tint = GeodouroGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Galeria",
                                color = GeodouroGreen,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.capturedImageUris.isNotEmpty(),
                enter = fadeIn() + slideInVertically { it / 2 }
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
                                    text = "${uiState.capturedImageUris.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "imagem pronta para analise",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeodouroTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(
                            onClick = { viewModel.clearCaptures() },
                            enabled = !uiState.isProcessing
                        ) {
                            Text(
                                "Limpar",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (!uiState.isProcessing) {
                                    GeodouroTextSecondary
                                } else {
                                    GeodouroGrey.copy(alpha = 0.4f)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(18.dp),
                color = GeodouroCardBg
            ) {
                Button(
                    onClick = { viewModel.analyzeSelection() },
                    enabled = !uiState.isProcessing && uiState.capturedImageUris.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = geodouroPrimaryButtonColors(),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    val label = when {
                        uiState.isProcessing -> "A analisar..."
                        uiState.capturedImageUris.isEmpty() -> "Analisar imagem"
                        else -> "Analisar 1 imagem"
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = GeodouroCardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
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
                            text = "Usa uma foto nítida da folha, flor ou fruto para melhorar a identificação.",
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
