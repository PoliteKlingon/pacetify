package com.example.pacetify.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.pacetify.MainActivity
import com.example.pacetify.data.source.preferenceFiles.SettingsPreferenceFile
import com.example.pacetify.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun sliderProgressToTime(progress: Int): Int {
        return (progress + 1) * 10
    }

    private fun timeToSliderProgress(time: Int): Int {
        return (time / 10) - 1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val settingsViewModel =
            ViewModelProvider(this).get(SettingsViewModel::class.java)

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val mainActivity = requireActivity() as MainActivity

        /*val textView: TextView = binding.textNotifications
        notificationsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }*/

        val settingsFile = SettingsPreferenceFile.getInstance(mainActivity)

        binding.swMotivate.isChecked = settingsFile.motivate
        binding.swRest.isChecked = settingsFile.rest
        binding.sbRest.progress = timeToSliderProgress(settingsFile.restTime)
        binding.sbRest.isEnabled = binding.swRest.isChecked
        binding.tvRest.text = "Maximal resting time: " + settingsFile.restTime + " s"

        binding.sbRest.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekbar: SeekBar?, progress: Int, fromUser: Boolean) {
                val curRestTime = sliderProgressToTime(progress)
                binding.tvRest.text = "Maximal resting time: $curRestTime s"

                settingsFile.restTime = curRestTime
                if (mainActivity.serviceBound)
                        mainActivity.pacetifyService?.notifySettingsChanged()
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}

            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        binding.swMotivate.setOnClickListener {
            settingsFile.motivate = binding.swMotivate.isChecked
            if (mainActivity.serviceBound)
                mainActivity.pacetifyService?.notifySettingsChanged()
        }

        binding.swRest.setOnClickListener {
            settingsFile.rest = binding.swRest.isChecked
            binding.sbRest.isEnabled = binding.swRest.isChecked
            if (mainActivity.serviceBound)
                mainActivity.pacetifyService?.notifySettingsChanged()
        }

        return root
    }
}