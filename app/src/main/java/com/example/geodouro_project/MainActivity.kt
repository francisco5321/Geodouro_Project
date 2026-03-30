package com.example.geodouro_project

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.geodouro_project.core.network.ConnectivityChecker
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.domain.model.LocalInferenceResult
import com.example.geodouro_project.ui.components.BottomNavigationBar
import com.example.geodouro_project.ui.screens.CaptureScreen
import com.example.geodouro_project.ui.screens.CommunityScreen
import com.example.geodouro_project.ui.screens.HomeScreen
import com.example.geodouro_project.ui.screens.IdentifyScreen
import com.example.geodouro_project.ui.screens.ObservationDetailScreen
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
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val connectivityChecker = remember(appContext) { ConnectivityChecker(appContext) }
    val repository = remember(appContext) { AppContainer.providePlantRepository(appContext) }
    val hasInternet by connectivityChecker.observeInternetAvailability().collectAsStateWithLifecycle(
        initialValue = connectivityChecker.hasInternetConnection()
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    val snackbarHostState = remember { SnackbarHostState() }
    var latestInferenceResult by remember { mutableStateOf<LocalInferenceResult?>(null) }
    var latestMultiImageUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var latestCaptureLatitude by remember { mutableStateOf<Double?>(null) }
    var latestCaptureLongitude by remember { mutableStateOf<Double?>(null) }
    var networkRefreshVersion by remember { mutableStateOf(0) }
    var previousInternetState by remember { mutableStateOf<Boolean?>(null) }

    val bottomNavRoutes = listOf("home", "community", "identify", "list", "profile")
    val showBottomBar = currentRoute in bottomNavRoutes

    LaunchedEffect(hasInternet) {
        val previous = previousInternetState
        previousInternetState = hasInternet

        if (hasInternet && previous == false) {
            networkRefreshVersion += 1
            repository.syncPendingObservations()
            snackbarHostState.showSnackbar("Ligacao restaurada. Dados atualizados.")
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
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
                HomeScreen(
                    onSpeciesClick = { speciesId ->
                        navController.navigate("speciesDetail/${Uri.encode(speciesId)}")
                    },
                    onOpenSpeciesList = {
                        navController.navigate("list")
                    }
                )
            }

            composable("community") {
                CommunityScreen(
                    refreshTrigger = networkRefreshVersion,
                    onSpeciesClick = { speciesId ->
                        navController.navigate("speciesDetail/${Uri.encode(speciesId)}")
                    }
                )
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
                ProfileScreen(
                    onObservationClick = { observationId ->
                        navController.navigate("observationDetail/${Uri.encode(observationId)}")
                    }
                )
            }

            composable("results") {
                ResultsScreen(
                    refreshTrigger = networkRefreshVersion,
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
                    onBackClick = { navController.popBackStack() },
                    onObservationClick = { observationId ->
                        navController.navigate("observationDetail/${Uri.encode(observationId)}")
                    }
                )
            }

            composable(
                route = "observationDetail/{observationId}",
                arguments = listOf(navArgument("observationId") { defaultValue = "" })
            ) { backStackEntry ->
                ObservationDetailScreen(
                    observationId = backStackEntry.arguments?.getString("observationId").orEmpty(),
                    onBackClick = { navController.popBackStack() },
                    onOpenSpecies = { speciesId ->
                        navController.navigate("speciesDetail/${Uri.encode(speciesId)}")
                    }
                )
            }
        }
    }
}
