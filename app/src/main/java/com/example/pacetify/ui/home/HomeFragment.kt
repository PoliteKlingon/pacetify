package com.example.pacetify.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.pacetify.MainActivity
import com.example.pacetify.R
import com.example.pacetify.databinding.FragmentHomeBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.CancellationException

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    private var cadenceFlow: MutableStateFlow<String>? = null
    private var homeTextFlow: MutableStateFlow<String>? = null
    private var songNameFlow: MutableStateFlow<String>? = null

    private var cadenceFlowObserver: Job? = null
    private var homeTextFlowObserver: Job? = null
    private var songNameFlowObserver: Job? = null

    private lateinit var mainActivity: MainActivity
    private var serviceBoundFlowObserver: Job? = null

    private fun onServiceConnected() {
        binding.onOff.text = getString(R.string.service_on)

        if (mainActivity.pacetifyService == null) {
            return
        }
        val flows = mainActivity.pacetifyService?.getFlows()
        if (flows != null) {
            cadenceFlow = flows[0]
            homeTextFlow = flows[1]
            songNameFlow = flows[2]
        }

        if (cadenceFlow == null || homeTextFlow == null || songNameFlow == null) {
            Log.w("HomeFragment", "Some of the service's flows are null, aborting...")
            return
        }

        Log.d("HomeFragment", "service connected")

        binding.textHome.text = homeTextFlow?.value
        binding.displayCadence.text = cadenceFlow?.value
        binding.displaySong.text = songNameFlow?.value

        if (cadenceFlowObserver == null) {
            cadenceFlowObserver = lifecycleScope.launchWhenStarted {
                cadenceFlow?.collectLatest {
                    binding.displayCadence.text = it
                    Log.d("HomeFragment", "cadence updated")
                }
            }
        }

        if (homeTextFlowObserver == null) {
            homeTextFlowObserver = lifecycleScope.launchWhenStarted {
                homeTextFlow?.collectLatest {
                    binding.textHome.text = it
                    Log.d("HomeFragment", "homeText updated")
                }
            }
        }

        if (songNameFlowObserver == null) {
            songNameFlowObserver = lifecycleScope.launchWhenStarted {
                songNameFlow?.collectLatest {
                    binding.displaySong.text = it
                    Log.d("HomeFragment", "songName updated")
                }
            }
        }

        binding.skipSong.isEnabled = mainActivity.serviceBound
    }

    private fun onServiceDisconnected() {
        binding.onOff.text = getString(R.string.service_off)

        binding.displayCadence.text = getString(R.string.service_off_description)
        binding.textHome.text = ""
        binding.displaySong.text = ""
        binding.skipSong.isEnabled = mainActivity.serviceBound

        cadenceFlow = null
        homeTextFlow = null
        songNameFlow = null

        cadenceFlowObserver?.cancel(CancellationException())
        cadenceFlowObserver = null
        homeTextFlowObserver?.cancel(CancellationException())
        homeTextFlowObserver = null
        songNameFlowObserver?.cancel(CancellationException())
        songNameFlowObserver = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onServiceDisconnected()
        serviceBoundFlowObserver?.cancel(CancellationException())
        _binding = null
    }

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        mainActivity = requireActivity() as MainActivity

        serviceBoundFlowObserver = lifecycleScope.launchWhenStarted {
            mainActivity.serviceBoundFlow.collectLatest {
                if (it) onServiceConnected() else onServiceDisconnected()
            }
        }

        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = /*it*/ getString(R.string.home_text_default)
        }

        binding.skipSong.isEnabled = mainActivity.serviceBound

        binding.skipSong.setOnClickListener {
            if (mainActivity.serviceBound) {
                mainActivity.pacetifyService?.skipSong()
            } else {
                Toast.makeText(requireActivity(), "Service is not active", Toast.LENGTH_SHORT).show()
            }
        }

        binding.onOff.setOnClickListener {
            if (mainActivity.serviceBound) {
                mainActivity.unbindService()
                mainActivity.stopService()
            } else {
                mainActivity.startService()
                mainActivity.bindService()
            }
        }

        if (cadenceFlow == null) binding.displayCadence.text = getString(R.string.service_off_description)

        if (homeTextFlow == null) binding.textHome.text = ""

        if (songNameFlow == null) binding.displaySong.text = ""

        val requestPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                binding.onOff.isEnabled = true
                binding.displayCadence.text = getString(R.string.service_off_description)
            } else {
                binding.displayCadence.text = getString(R.string.activity_recognition_disabled_warning)
                binding.onOff.isEnabled = false
            }
        }

        if (ContextCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            requestPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)

        return root
    }

    override fun onResume() {
        super.onResume()
        if (mainActivity.serviceBound) {
            onServiceConnected()
        }
    }

    override fun onPause() {
        super.onPause()
        if (mainActivity.serviceBound) {
            onServiceDisconnected()
        }
    }
}