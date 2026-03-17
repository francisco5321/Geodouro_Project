package com.example.geodouro_project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import com.example.geodouro_project.domain.model.LocalInferenceResult
import com.example.geodouro_project.ui.components.BottomNavigationBar
import com.example.geodouro_project.ui.screens.*
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
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    var latestInferenceResult by remember { mutableStateOf<LocalInferenceResult?>(null) }
    
    // Rotas que mostram bottom navigation
    val bottomNavRoutes = listOf("home", "community", "identify", "list", "profile")
    val showBottomBar = currentRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            // Pop até home para evitar pilha de navegação grande
                            popUpTo("home") {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("home") {
                HomeScreen()
            }
            
            composable("community") {
                CommunityScreen()
            }
            
            composable("identify") {
                IdentifyScreen(
                    onIdentifyClick = { inferenceResult ->
                        latestInferenceResult = inferenceResult
                        navController.navigate("results")
                    }
                )
            }
            
            composable("list") {
                SpeciesListScreen()
            }
            
            composable("profile") {
                ProfileScreen()
            }
            
            composable("results") {
                ResultsScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onConfirmResult = { result ->
                        // Navegar para detalhes ou voltar
                        navController.popBackStack()
                    },
                    localInferenceResult = latestInferenceResult ?: LocalInferenceResult(
                        imageUri = "",
                        latitude = null,
                        longitude = null,
                        predictedSpecies = "Sem inferencia local",
                        confidence = 0f
                    )
                )
            }
            
            composable("capture") {
                CaptureScreen()
            }
        }
    }
}