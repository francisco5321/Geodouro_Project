package com.example.geodouro_project

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.geodouro_project.domain.model.LocalInferenceResult
import com.example.geodouro_project.ui.components.BottomNavigationBar
import com.example.geodouro_project.ui.screens.CaptureScreen
import com.example.geodouro_project.ui.screens.CommunityScreen
import com.example.geodouro_project.ui.screens.HomeScreen
import com.example.geodouro_project.ui.screens.IdentifyScreen
import com.example.geodouro_project.ui.screens.ProfileScreen
import com.example.geodouro_project.ui.screens.ResultsScreen
import com.example.geodouro_project.ui.screens.SpeciesDetailScreen
import com.example.geodouro_project.ui.screens.SpeciesListScreen
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

@androidx.compose.runtime.Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    var latestInferenceResult by remember { mutableStateOf<LocalInferenceResult?>(null) }
    var latestMultiImageUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var latestCaptureLatitude by remember { mutableStateOf<Double?>(null) }
    var latestCaptureLongitude by remember { mutableStateOf<Double?>(null) }

    val bottomNavRoutes = listOf("home", "community", "identify", "list", "profile")
    val showBottomBar = currentRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
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
                        latestMultiImageUris = emptyList()
                        latestCaptureLatitude = inferenceResult.latitude
                        latestCaptureLongitude = inferenceResult.longitude
                        navController.navigate("results")
                    },
                    onIdentifyMultipleClick = { imageUris, latitude, longitude ->
                        latestInferenceResult = null
                        latestMultiImageUris = imageUris
                        latestCaptureLatitude = latitude
                        latestCaptureLongitude = longitude
                        navController.navigate("results")
                    }
                )
            }

            composable("list") {
                SpeciesListScreen(
                    onSpeciesClick = { speciesId ->
                        navController.navigate("speciesDetail/${Uri.encode(speciesId)}")
                    }
                )
            }

            composable("profile") {
                ProfileScreen()
            }

            composable("results") {
                ResultsScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onConfirmResult = {
                        navController.popBackStack()
                    },
                    multiImageUris = latestMultiImageUris,
                    captureLatitude = latestCaptureLatitude,
                    captureLongitude = latestCaptureLongitude,
                    localInferenceResult = latestInferenceResult ?: LocalInferenceResult(
                        imageUri = "",
                        latitude = latestCaptureLatitude,
                        longitude = latestCaptureLongitude,
                        predictedSpecies = "Sem inferencia local",
                        confidence = 0f
                    )
                )
            }

            composable("capture") {
                CaptureScreen()
            }

            composable(
                route = "speciesDetail/{speciesId}",
                arguments = listOf(navArgument("speciesId") { defaultValue = "" })
            ) { backStackEntry ->
                SpeciesDetailScreen(
                    speciesId = backStackEntry.arguments?.getString("speciesId").orEmpty(),
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
