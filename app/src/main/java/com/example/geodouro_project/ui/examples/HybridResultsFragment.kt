package com.example.geodouro_project.ui.examples

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.example.geodouro_project.domain.model.LocalInferenceResult
import com.example.geodouro_project.ui.screens.ResultsScreen
import com.example.geodouro_project.ui.theme.Geodouro_ProjectTheme

class HybridResultsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val localInferenceResult = buildLocalInferenceResult(requireArguments())

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Geodouro_ProjectTheme {
                    ResultsScreen(
                        onBackClick = { parentFragmentManager.popBackStack() },
                        onConfirmResult = { parentFragmentManager.popBackStack() },
                        localInferenceResult = localInferenceResult
                    )
                }
            }
        }
    }

    private fun buildLocalInferenceResult(bundle: Bundle): LocalInferenceResult {
        val hasLatitude = bundle.containsKey(ARG_LATITUDE)
        val hasLongitude = bundle.containsKey(ARG_LONGITUDE)

        return LocalInferenceResult(
            imageUri = bundle.getString(ARG_IMAGE_URI).orEmpty(),
            capturedAt = bundle.getLong(ARG_CAPTURED_AT, System.currentTimeMillis()),
            latitude = if (hasLatitude) bundle.getDouble(ARG_LATITUDE) else null,
            longitude = if (hasLongitude) bundle.getDouble(ARG_LONGITUDE) else null,
            predictedSpecies = bundle.getString(ARG_PREDICTED_SPECIES).orEmpty(),
            confidence = bundle.getFloat(ARG_CONFIDENCE)
        )
    }

    companion object {
        private const val ARG_IMAGE_URI = "arg_image_uri"
        private const val ARG_CAPTURED_AT = "arg_captured_at"
        private const val ARG_LATITUDE = "arg_latitude"
        private const val ARG_LONGITUDE = "arg_longitude"
        private const val ARG_PREDICTED_SPECIES = "arg_predicted_species"
        private const val ARG_CONFIDENCE = "arg_confidence"

        fun newInstance(result: LocalInferenceResult): HybridResultsFragment {
            return HybridResultsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_IMAGE_URI, result.imageUri)
                    putLong(ARG_CAPTURED_AT, result.capturedAt)
                    result.latitude?.let { putDouble(ARG_LATITUDE, it) }
                    result.longitude?.let { putDouble(ARG_LONGITUDE, it) }
                    putString(ARG_PREDICTED_SPECIES, result.predictedSpecies)
                    putFloat(ARG_CONFIDENCE, result.confidence)
                }
            }
        }
    }
}
