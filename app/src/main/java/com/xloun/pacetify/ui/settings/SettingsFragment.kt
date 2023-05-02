package com.xloun.pacetify.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.xloun.pacetify.MainActivity
import com.xloun.pacetify.R
import com.xloun.pacetify.data.source.preferenceFiles.SettingsPreferenceFile
import com.xloun.pacetify.data.source.preferenceFiles.Theme
import com.xloun.pacetify.databinding.FragmentSettingsBinding

/**
 * In this fragment the user can change the basic settings for the app - motivating, rest and if
 * the rest is enabled, they can adjust the maximal resting time before an upbeat song is played
 * again. Here the user can also choose the color theme for Pacetify.
 * The settings are stored in SettingPreferenceFile.
 *
 * author: Jiří Loun
 */

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // the slider progress bar needs these two functions to convert between steps on the bar and
    // the resting time. The bar goes from 0, hence the '+ 1'
    private fun sliderProgressToTime(progress: Int): Int { return (progress + 1) * 10 }
    private fun timeToSliderProgress(time: Int): Int { return (time / 10) - 1 }

    // The mode selection buttons custom appearance changes
    private fun modeButtonSelect(toSelect: TextView, toDeselect: TextView) {
        toDeselect.setTextColor(binding.btnWalk.hintTextColors.defaultColor)
        toDeselect.setBackgroundResource(R.drawable.blank_outline)
        toSelect.setTextColor(binding.btnRun.linkTextColors)
        toSelect.setBackgroundResource(R.drawable.radius_outline)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // I need this activity reference to be able to communicate with the service through it
        val mainActivity = requireActivity() as MainActivity
        val settingsFile = SettingsPreferenceFile.getInstance(mainActivity)

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        if (settingsFile.walkingMode) modeButtonSelect(binding.btnWalk, binding.btnRun)
        else modeButtonSelect(binding.btnRun, binding.btnWalk)

        // running mode has been selected
        binding.btnRun.setOnClickListener {
            modeButtonSelect(binding.btnRun, binding.btnWalk)
            settingsFile.walkingMode = false
            mainActivity.notifyServiceSettings()
        }

        // walking mode has been selected
        binding.btnWalk.setOnClickListener {
            modeButtonSelect(binding.btnWalk, binding.btnRun)
            settingsFile.walkingMode = true
            mainActivity.notifyServiceSettings()
        }

        binding.swMotivate.isChecked = settingsFile.motivate
        binding.swMotivate.setOnClickListener {
            settingsFile.motivate = binding.swMotivate.isChecked
            mainActivity.notifyServiceSettings()
        }

        binding.swRest.isChecked = settingsFile.rest
        binding.swRest.setOnClickListener {
            settingsFile.rest = binding.swRest.isChecked
            binding.sbRest.isEnabled = binding.swRest.isChecked
            mainActivity.notifyServiceSettings()
        }

        binding.tvRest.text = "Maximal resting time: ${settingsFile.restTime} s"

        binding.sbRest.progress = timeToSliderProgress(settingsFile.restTime)
        // enable the slider only if rest option is enabled
        binding.sbRest.isEnabled = binding.swRest.isChecked
        binding.sbRest.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekbar: SeekBar?, progress: Int, fromUser: Boolean) {
                val curRestTime = sliderProgressToTime(progress)
                binding.tvRest.text = "Maximal resting time: $curRestTime s"

                settingsFile.restTime = curRestTime
                mainActivity.notifyServiceSettings()
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        val theme = SettingsPreferenceFile.getInstance(mainActivity).theme
        // check the current theme
        binding.rgTheme.check(
            when (theme) {
                Theme.DEFAULT -> binding.rbDefault.id
                Theme.FIRE    -> binding.rbFire.id
                Theme.WATER   -> binding.rbWater.id
                Theme.EARTH   -> binding.rbEarth.id
                Theme.AIR     -> binding.rbAir.id
            }
        )
        // react to theme changes
        binding.rgTheme.setOnCheckedChangeListener { _, checkedId ->
            SettingsPreferenceFile.getInstance(mainActivity).theme = when (checkedId) {
                binding.rbDefault.id -> Theme.DEFAULT
                binding.rbFire.id    -> Theme.FIRE
                binding.rbWater.id   -> Theme.WATER
                binding.rbEarth.id   -> Theme.EARTH
                binding.rbAir.id     -> Theme.AIR
                else -> Theme.DEFAULT
            }
            mainActivity.recreate()
        }

        binding.swBackground.isChecked = settingsFile.addBackground
        binding.swBackground.setOnClickListener {
            settingsFile.addBackground = binding.swBackground.isChecked
            mainActivity.recreate()
        }

        return root
    }
}