package com.example.pacetify.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.pacetify.MainActivity
import com.example.pacetify.data.source.preferenceFiles.SettingsPreferenceFile
import com.example.pacetify.databinding.FragmentSettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * In this fragment the user can change the basic settings for the app - motivating, rest and if
 * the rest is enabled, they can adjust the maximal resting time before an upbeat song is played
 * again. The settings are store in SettingPreferenceFile.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // we need this activity reference to be able to communicate with the service through it
        val mainActivity = requireActivity() as MainActivity
        val settingsViewModel by viewModels<SettingsViewModel>()

        // observe our viewmodel data
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    settingsViewModel.isCheckedMotivate.collect {
                        binding.swMotivate.isChecked = it
                    }
                }
                launch {
                    settingsViewModel.isCheckedRest.collectLatest {
                        binding.swRest.isChecked = it
                        // enable the slider only if rest option is enabled
                        binding.sbRest.isEnabled = it
                    }
                }
                launch {
                    settingsViewModel.restBarProgress.collectLatest {
                        binding.sbRest.progress = it
                    }
                }
                launch {
                    settingsViewModel.restTime.collectLatest {
                        binding.tvRest.text = "Maximal resting time: $it s"
                    }
                }
            }
        }

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.sbRest.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekbar: SeekBar?, progress: Int, fromUser: Boolean) {
                settingsViewModel.setRestTime(progress)
                mainActivity.notifyServiceSettings()
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        binding.swMotivate.setOnClickListener {
            settingsViewModel.setMotivate(binding.swMotivate.isChecked)
            mainActivity.notifyServiceSettings()
        }

        binding.swRest.setOnClickListener {
            settingsViewModel.setRest(binding.swRest.isChecked)
            mainActivity.notifyServiceSettings()
        }


        return root
    }
}