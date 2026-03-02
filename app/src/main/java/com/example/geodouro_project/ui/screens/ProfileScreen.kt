package com.example.geodouro_project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.geodouro_project.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Perfil",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = GeodouroTextPrimary
                    )
                },
                actions = {
                    IconButton(onClick = { /* Settings */ }) {
                        Icon(Icons.Default.Settings, "Definições", tint = GeodouroGrey)
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Cabeçalho do perfil
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
                    elevation = CardDefaults.cardElevation(2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(40.dp))
                                .background(GeodouroLightGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Utilizador GEODOURO",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = GeodouroTextPrimary
                        )

                        Text(
                            "utilizador@geodouro.pt",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GeodouroTextSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Estatísticas
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem("24", "Observações")
                            StatItem("12", "Validadas")
                            StatItem("8", "Espécies")
                        }
                    }
                }
            }

            item {
                Text(
                    "Configurações",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GeodouroTextPrimary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            item {
                ProfileMenuItem(
                    icon = Icons.Default.Person,
                    title = "Editar Perfil",
                    onClick = { /* Edit profile */ }
                )
            }

            item {
                ProfileMenuItem(
                    icon = Icons.Default.Notifications,
                    title = "Notificações",
                    onClick = { /* Notifications */ }
                )
            }

            item {
                ProfileMenuItem(
                    icon = Icons.Default.LocationOn,
                    title = "Localização",
                    onClick = { /* Location settings */ }
                )
            }

            item {
                ProfileMenuItem(
                    icon = Icons.Default.Help,
                    title = "Ajuda e Suporte",
                    onClick = { /* Help */ }
                )
            }

            item {
                ProfileMenuItem(
                    icon = Icons.Default.Info,
                    title = "Acerca",
                    onClick = { /* About */ }
                )
            }

            item {
                Button(
                    onClick = { /* Logout */ },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red.copy(alpha = 0.1f),
                        contentColor = Color.Red
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ExitToApp, "Terminar sessão")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Terminar Sessão")
                }
            }
        }
    }
}

@Composable
fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = GeodouroGreen
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = GeodouroTextSecondary
        )
    }
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = GeodouroGreen,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = GeodouroTextPrimary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.NavigateNext,
                contentDescription = null,
                tint = GeodouroGrey
            )
        }
    }
}
