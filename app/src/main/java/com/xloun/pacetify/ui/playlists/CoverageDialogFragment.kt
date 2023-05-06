package com.xloun.pacetify.ui.playlists

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.children
import androidx.fragment.app.DialogFragment
import com.xloun.pacetify.R
import com.xloun.pacetify.data.Song
import com.xloun.pacetify.databinding.DialogFragmentCoverageBinding
import com.xloun.pacetify.util.NumberUtils
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class CoverageDialogFragment(
        private val intervals: Array<Int>,
        private val runScore: Double,
        private val walkScore: Double
        ): DialogFragment() {
    private var _binding: DialogFragmentCoverageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        isCancelable = true
        _binding = DialogFragmentCoverageBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.tvTitle.text = "BPM coverage\nRun ${NumberUtils.roundToTwoDecPts(runScore)}/10, " +
                "Walk ${NumberUtils.roundToTwoDecPts(walkScore)}/10"

        fun dpToPx(dp: Int): Int {
            val density = requireContext().resources.displayMetrics.density;
            return  dp * density.toInt()
        }

        val maxValue = intervals.max()
        binding.tvYAxisMax.text = maxValue.coerceAtLeast(50).toString()
        binding.tvYAxisMedium.text = (maxValue.coerceAtLeast(50) / 2).toString()
        // maximum of 250 dp, minimum of 50 songs, scale with max count
        val multiplier = (50.0 / maxValue).coerceAtMost(1.0) * 5

        for ((index, child) in binding.llGraph.children.withIndex()) {
            child.requestLayout()
            child.layoutParams.height = dpToPx((intervals[index] * multiplier).toInt())
            if (intervals[index] > 30) {
                (child as ImageView).setImageResource(R.color.lime_200)
            } else if (intervals[index] > 15) {
                (child as ImageView).setImageResource(R.color.amber_200)
            } else {
                (child as ImageView).setImageResource(R.color.deep_orange_200)
            }
        }
        return root
    }

}