package com.example.geodouro_project.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.geodouro_project.ui.theme.geodouroPrimaryButtonColors

@Composable
fun CaptureScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Nova observacao", style = MaterialTheme.typography.headlineMedium)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text("Aqui vai aparecer a imagem capturada.", modifier = Modifier.padding(16.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sugestao da IA:", style = MaterialTheme.typography.labelLarge)
                Text("Lavandula stoechas (Rosmaninho)", style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(progress = { 0.85f }, modifier = Modifier.fillMaxWidth())
                Text("90% de confianca", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { },
            modifier = Modifier.fillMaxWidth(),
            colors = geodouroPrimaryButtonColors()
        ) {
            Text("Confirmar e enviar")
        }
    }
}
