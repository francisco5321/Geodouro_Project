package com.example.geodouro_project.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.geodouro_project.ui.theme.GeodouroGreen
import com.example.geodouro_project.ui.theme.GeodouroGrey
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentifyScreen(onIdentifyClick: (LocalInferenceResult) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val classifier = remember { MobileNetV3Classifier(context.applicationContext) }
    val snackbarHostState = remember { SnackbarHostState() }
    var isProcessing by rememberSaveable { mutableStateOf(false) }

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
            isProcessing = true

            runCatching {
                val prediction = classifier.classify(bitmap)
                val imageUri = saveCapturedBitmap(context, bitmap)

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
                        latitude = null,
                        longitude = null,
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
            }.onFailure {
                snackbarHostState.showSnackbar(
                    "Falha na identificacao local: ${it.message ?: "erro desconhecido"}"
                )
            }

            isProcessing = false
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GeodouroWhite),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
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
                        text = "Sem localizacao GPS",
                        style = MaterialTheme.typography.bodySmall,
                        color = GeodouroGrey
                    )
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
                    text = if (isProcessing) "A identificar..." else "Toque para identificar",
                    style = MaterialTheme.typography.bodyLarge,
                    color = GeodouroTextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

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
                    modifier = Modifier.size(120.dp),
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
                            contentDescription = "Identificar",
                            modifier = Modifier.size(48.dp),
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

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
                                text = "Adicione ${MobileNetV3Classifier.DEFAULT_MODEL_FILE} e labels para inferencia final.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GeodouroTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
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
