package com.example.geodouro_project.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class LocationResolver(
    private val appContext: Context
) {

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCurrentCoordinates(): Pair<Double, Double>? {
        if (!hasLocationPermission()) {
            return null
        }

        val fusedCoordinates = getFusedLocationCoordinates()
        if (fusedCoordinates != null) {
            return fusedCoordinates
        }

        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (provider == null) {
            return getLastKnownLocationCoordinates(locationManager)
        }

        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(appContext)
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }

            runCatching {
                locationManager.getCurrentLocation(provider, cancellationSignal, executor) { location ->
                    if (continuation.isActive) {
                        continuation.resume(location?.let { it.latitude to it.longitude })
                    }
                }
            }.onFailure {
                if (continuation.isActive) {
                    continuation.resume(getLastKnownLocationCoordinates(locationManager))
                }
            }
        }
    }

    private suspend fun getFusedLocationCoordinates(): Pair<Double, Double>? {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)

        return suspendCancellableCoroutine { continuation ->
            runCatching {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        if (continuation.isActive) {
                            continuation.resume(location?.let { it.latitude to it.longitude })
                        }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
            }.onFailure {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        } ?: suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (continuation.isActive) {
                        continuation.resume(location?.let { it.latitude to it.longitude })
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        }
    }

    private fun getLastKnownLocationCoordinates(locationManager: LocationManager): Pair<Double, Double>? {
        return sequenceOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
    }

    suspend fun getLocationLabel(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) {
            return null
        }

        val geocoder = Geocoder(appContext, Locale.getDefault())
        @Suppress("DEPRECATION")
        val address = runCatching {
            geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
        }.getOrNull() ?: return null

        return buildList {
            address.thoroughfare?.takeIf { it.isNotBlank() }?.let(::add)
            address.subLocality?.takeIf { it.isNotBlank() }?.let(::add)
            address.locality?.takeIf { it.isNotBlank() }?.let(::add)
            address.adminArea?.takeIf { it.isNotBlank() }?.let(::add)
        }.distinct().joinToString(", ").takeIf { it.isNotBlank() }
    }
}
