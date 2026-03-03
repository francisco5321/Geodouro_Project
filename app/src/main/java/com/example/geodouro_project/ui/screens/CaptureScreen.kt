package com.example.geodouro_project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CaptureScreen() {
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text("Nova Observação", style = MaterialTheme.typography.headlineMedium)

        //Placeholder da camera
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text("aqui vai ficar a imnagem capturada", modifier = Modifier.padding(16.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        //aqui vai ser a identificação por AI
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sugestão da AI:", style = MaterialTheme.typography.labelLarge)
                Text("Lavandula stoechas (Rosmaninho)", style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(progress = { 0.85f}, modifier = Modifier.fillMaxWidth())
                Text("90% de confiança", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { /* guardar e georreferenciar*/ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Confirmar e enviar")
        }
    }
}