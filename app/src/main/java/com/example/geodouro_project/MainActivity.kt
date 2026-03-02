package com.example.geodouro_project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.geodouro_project.ui.screens.HomeScreen
import com.example.geodouro_project.ui.screens.CaptureScreen
import com.example.geodouro_project.ui.theme.Geodouro_ProjectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Geodouro_ProjectTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToCapture = { navController.navigate("capture") },
                onNavigateToMap = { /* navController.navigate("map") */ }
            )
        }
        composable("capture") {
            CaptureScreen()
        }
        // Quando criar a MapScreen, adicione o composable("map") aqui
    }
}