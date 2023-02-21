package com.example.pacetify.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import com.example.pacetify.data.PacetifyService
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

    fun onServiceConnected() {
        binding.onOff.text = "ON"

        if (mainActivity.pacetifyService == null) {
            return
        }
        val flows = mainActivity.pacetifyService?.getFlows()
        if (flows != null) {
            cadenceFlow = flows[0]
            homeTextFlow = flows[1]
            songNameFlow = flows[2]
        }

        Log.d("HomeFragment", "service connected")

        cadenceFlowObserver = lifecycleScope.launchWhenStarted {
            cadenceFlow?.collectLatest {
                binding.displayCadence.text = it
                Log.d("HomeFragment", "cadence updated")
            }
        }
        homeTextFlowObserver = lifecycleScope.launchWhenStarted {
            homeTextFlow?.collectLatest {
                binding.textHome.text = it
                Log.d("HomeFragment", "hometext updated")
            }
        }
        songNameFlowObserver = lifecycleScope.launchWhenStarted {
            songNameFlow?.collectLatest {
                binding.displaySong.text = it
                Log.d("HomeFragment", "songname updated")
            }
        }

        binding.skipSong.isEnabled = mainActivity.serviceBound
    }

    private fun onServiceDisconnected() {
        binding.onOff.text = "OFF"

        binding.displayCadence.text = "You have to start the Service first"
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
        //if (serviceBound) unbindService()
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

        //bindService()

        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = /*it*/ "Spotify Running App"
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
                Intent(requireActivity(), PacetifyService::class.java).also {
                    requireActivity().stopService(it)
                }
            } else {
                Intent(requireActivity(), PacetifyService::class.java).also {
                    requireActivity().startService(it)
                }
                mainActivity.bindService()
            }
        }

        if (cadenceFlow == null) binding.displayCadence.text = "You have to start the Service first"

        if (homeTextFlow == null) binding.textHome.text = ""

        if (songNameFlow == null) binding.displaySong.text = ""

        val requestPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                binding.onOff.isEnabled = true
                binding.displayCadence.text = "You have to start the Service first"
            } else {
                binding.displayCadence.text = "This app will not work without activity recognition enabled. " +
                        "Please add this permission in settings."
                binding.onOff.isEnabled = false
            }
        }

        if (ContextCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) requestPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)

        return root
    }
}