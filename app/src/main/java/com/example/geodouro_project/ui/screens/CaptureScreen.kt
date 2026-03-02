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

        // Placeholder para a Câmera/Preview da Imagem
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text("Preview da Câmera / Imagem Selecionada", modifier = Modifier.padding(16.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Simulação de Identificação por IA
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sugestão da IA (Base Flora-On):", style = MaterialTheme.typography.labelLarge)
                Text("Lavandula stoechas (Rosmaninho)", style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(progress = { 0.85f }, modifier = Modifier.fillMaxWidth())
                Text("Confiança: 85%", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { /* Lógica para salvar e georreferenciar */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Confirmar e Enviar")
        }
    }
}