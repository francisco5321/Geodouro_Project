package com.example.geodouro_project.ui.examples

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.geodouro_project.domain.model.LocalInferenceResult
import com.example.geodouro_project.ui.screens.ResultsScreen
import com.example.geodouro_project.ui.theme.Geodouro_ProjectTheme

class HybridResultsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val localInferenceResult = intent.toLocalInferenceResult()

        setContent {
            Geodouro_ProjectTheme {
                ResultsScreen(
                    onBackClick = { finish() },
                    onConfirmResult = { finish() },
                    localInferenceResult = localInferenceResult
                )
            }
        }
    }

    private fun Intent.toLocalInferenceResult(): LocalInferenceResult {
        val hasLatitude = hasExtra(EXTRA_LATITUDE)
        val hasLongitude = hasExtra(EXTRA_LONGITUDE)

        return LocalInferenceResult(
            imageUri = getStringExtra(EXTRA_IMAGE_URI).orEmpty(),
            capturedAt = getLongExtra(EXTRA_CAPTURED_AT, System.currentTimeMillis()),
            latitude = if (hasLatitude) getDoubleExtra(EXTRA_LATITUDE, 0.0) else null,
            longitude = if (hasLongitude) getDoubleExtra(EXTRA_LONGITUDE, 0.0) else null,
            predictedSpecies = getStringExtra(EXTRA_PREDICTED_SPECIES).orEmpty(),
            confidence = getFloatExtra(EXTRA_CONFIDENCE, 0.0f)
        )
    }

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_CAPTURED_AT = "extra_captured_at"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_PREDICTED_SPECIES = "extra_predicted_species"
        const val EXTRA_CONFIDENCE = "extra_confidence"
    }
}
