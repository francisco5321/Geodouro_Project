package com.example.geodouro_project.ui.screens

import android.net.Uri
import java.util.UUID

data class FloraObservation(
    val id: String = UUID.randomUUID().toString(),
    val speciesName: String? = null,
    val scientificName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val imageUri: Uri,
    val contributorName: String,
    val isValidated: Boolean = false
) {
    // Função para devolver a coordenada formatada no padrão Geodouro
    fun getFormattedCoordinates(): String {
        return "%.6f, %.6f".format(latitude, longitude)
    }
}