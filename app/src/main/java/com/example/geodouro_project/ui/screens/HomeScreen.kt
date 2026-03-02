package com.example.geodouro_project.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geodouro_project.ui.theme.GeodouroBg
import com.example.geodouro_project.ui.theme.GeodouroBlue
import com.example.geodouro_project.ui.theme.GeodouroLightBlue
import com.example.geodouro_project.ui.theme.GeodouroWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToCapture: () -> Unit, onNavigateToMap: () -> Unit) {
    Scaffold(
        containerColor = GeodouroBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    // Aqui você pode colocar o logótipo da Geodouro
                    Text("GEODOURO", style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GeodouroWhite,
                    titleContentColor = GeodouroBlue
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {

            Text(
                "SISTEMAS DE INFORMAÇÃO GEOGRÁFICA",
                style = MaterialTheme.typography.labelSmall,
                color = GeodouroLightBlue
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Botão Principal - Estilo "Call to Action" do site
            Button(
                onClick = onNavigateToCapture,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GeodouroBlue)
            ) {
                Icon(Icons.Default.AddAPhoto, null)
                Spacer(Modifier.width(8.dp))
                Text("EFETUAR LEVANTAMENTO")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Seção de Mapa
            OutlinedButton(
                onClick = onNavigateToMap,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, GeodouroBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Map, null, tint = GeodouroBlue)
                Text(" CONSULTAR MAPA SIG", color = GeodouroBlue)
            }
        }
    }
}