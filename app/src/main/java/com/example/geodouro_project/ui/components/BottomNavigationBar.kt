package com.example.geodouro_project.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.geodouro_project.ui.theme.*

enum class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    HOME("home", Icons.Default.Home, "Início"),
    COMMUNITY("community", Icons.Default.Group, "Comunidade"),
    IDENTIFY("identify", Icons.Default.CameraAlt, "Identificar"),
    LIST("list", Icons.Default.List, "Lista"),
    PROFILE("profile", Icons.Default.Person, "Perfil")
}

@Composable
fun BottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = GeodouroWhite,
        contentColor = GeodouroGrey,
        tonalElevation = 0.dp
    ) {
        BottomNavItem.entries.forEach { item ->
            if (item == BottomNavItem.IDENTIFY) {
                // Botão central especial (câmara)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    FloatingActionButton(
                        onClick = { onNavigate(item.route) },
                        containerColor = GeodouroBrandGreen,
                        contentColor = GeodouroWhite,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else {
                NavigationBarItem(
                    selected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) },
                    icon = {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GeodouroBrandGreen,
                        selectedTextColor = GeodouroBrandGreen,
                        unselectedIconColor = GeodouroGrey,
                        unselectedTextColor = GeodouroGrey,
                        indicatorColor = GeodouroCardBg
                    )
                )
            }
        }
    }
}
