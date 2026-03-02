package com.example.geodouro_project.ui.theme

import androidx.compose.foundation.background // Adicione este
import androidx.compose.foundation.layout.* // Este garante Row, Column, Box, Spacer, etc.
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.* // Este garante Card, Text, MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment           // Necessário para o alinhamento vertical
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.geodouro_project.ui.screens.FloraObservation

// Se o FloraObservation estiver noutro pacote, o import abaixo deve estar correto:
@Composable
fun FloraCard(observation: FloraObservation) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        // Agora o Row correto (do Compose) aceita o parâmetro modifier
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicador de cor lateral (estilo SIG da Geodouro)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(GeodouroLightGreen)
            )

            Spacer(Modifier.width(12.dp))

            Column {
                // Usamos o operador ?: para dar um nome padrão caso scientificName seja nulo
                Text(
                    text = (observation.scientificName ?: "Espécie Desconhecida").uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = GeodouroGreen
                )

                // Formatamos a latitude e longitude para parecer uma coordenada SIG
                Text(
                    text = "LAT: ${observation.latitude}, LON: ${observation.longitude}",
                    style = MaterialTheme.typography.bodySmall,
                    color = GeodouroGrey
                )
            }
        }
    }
}