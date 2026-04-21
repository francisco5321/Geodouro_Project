package com.example.geodouro_project.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class PersistentImageStorage(
    private val appContext: Context
) {

    fun saveBitmap(bitmap: Bitmap): String {
        val outputFile = createImageFile()
        FileOutputStream(outputFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
        return Uri.fromFile(outputFile).toString()
    }

    fun createCameraImageUri(): Uri {
        val outputFile = createImageFile()
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            outputFile
        )
    }

    fun importFromUri(sourceUri: Uri): String {
        val outputFile = createImageFile()
        appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Nao foi possivel ler a imagem selecionada.")
        return Uri.fromFile(outputFile).toString()
    }

    fun openInputStream(uriString: String) = when (val parsedUri = Uri.parse(uriString)) {
        null -> null
        else -> when (parsedUri.scheme) {
            "file" -> parsedUri.path?.let { FileInputStream(it) }
            else -> appContext.contentResolver.openInputStream(parsedUri)
        }
    }

    private fun createImageFile(): File {
        val capturesDir = File(appContext.filesDir, CAPTURES_DIRECTORY).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        return File(capturesDir, "observation_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
    }

    companion object {
        private const val CAPTURES_DIRECTORY = "observations"
        private const val JPEG_QUALITY = 95
    }
}
