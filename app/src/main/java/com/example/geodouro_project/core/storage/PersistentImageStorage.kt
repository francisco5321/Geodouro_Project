package com.example.geodouro_project.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        } ?: throw IllegalStateException("Não foi possível ler a imagem selecionada.")
        return Uri.fromFile(outputFile).toString()
    }

    fun decodeSampledBitmap(uriString: String, maxDimension: Int = MAX_DECODE_DIMENSION): Bitmap? {
        val parsedUri = Uri.parse(uriString) ?: return null
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        openInputStream(parsedUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maxDimension = maxDimension
            )
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return openInputStream(parsedUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    fun openInputStream(uriString: String) = when (val parsedUri = Uri.parse(uriString)) {
        null -> null
        else -> when (parsedUri.scheme) {
            "file" -> parsedUri.path?.let { FileInputStream(it) }
            else -> appContext.contentResolver.openInputStream(parsedUri)
        }
    }

    private fun openInputStream(uri: Uri) = when (uri.scheme) {
        "file" -> uri.path?.let { FileInputStream(it) }
        else -> appContext.contentResolver.openInputStream(uri)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) {
            return 1
        }

        var sampleSize = 1
        var sampledWidth = width
        var sampledHeight = height

        while (sampledWidth / 2 >= maxDimension || sampledHeight / 2 >= maxDimension) {
            sampleSize *= 2
            sampledWidth /= 2
            sampledHeight /= 2
        }

        return sampleSize.coerceAtLeast(1)
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
        private const val MAX_DECODE_DIMENSION = 640
    }
}
