package com.example.geodouro_project.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.geodouro_project.ui.theme.*

data class IdentificationResult(
    val scientificName: String,
    val commonName: String,
    val family: String,
    val confidence: Float,
    val imageCount: Int = 4
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(onBackClick: () -> Unit, onConfirmResult: (IdentificationResult) -> Unit) {
    val results = listOf(
        IdentificationResult("Lavandula stoechas", "Rosmaninho", "Lamiaceae", 0.67f),
        IdentificationResult("Digitalis purpurea", "Dedaleira", "Plantaginaceae", 0.28f),
        IdentificationResult("Erica australis", "Urze", "Ericaceae", 0.15f)
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Identificação - Resultados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "GEODOURO",
                            style = MaterialTheme.typography.labelSmall,
                            color = GeodouroGrey
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Filter */ }) {
                        Icon(Icons.Default.Close, "Fechar", tint = GeodouroGrey)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GeodouroWhite
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GeodouroWhite),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(results) { result ->
                ResultCard(
                    result = result,
                    onConfirm = { onConfirmResult(result) }
                )
            }
        }
    }
}

@Composable
fun ResultCard(result: IdentificationResult, onConfirm: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Grid de imagens (placeholder)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(GeodouroLightBg)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Nome científico
            Text(
                result.scientificName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GeodouroTextPrimary
            )

            // Nome comum
            Text(
                result.commonName,
                style = MaterialTheme.typography.bodyMedium,
                color = GeodouroTextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Família
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    result.family,
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroTextSecondary
                )
                Spacer(modifier = Modifier.weight(1f))
                
                // Ícone e percentagem de confiança
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = GeodouroGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "${(result.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = GeodouroGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Barra de progresso
            LinearProgressIndicator(
                progress = { result.confidence },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = GeodouroGreen,
                trackColor = GeodouroLightBg
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Botão confirmar
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GeodouroGreen
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirmar", color = Color.White)
            }
        }
    }
}
