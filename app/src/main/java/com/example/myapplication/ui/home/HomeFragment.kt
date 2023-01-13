package com.example.myapplication.ui.home

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
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
import com.example.myapplication.SRAService
import com.example.myapplication.databinding.FragmentHomeBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.CancellationException

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    private var sraService: SRAService? = null
    private var serviceBound: Boolean = false

    private var cadenceFlow: MutableStateFlow<String>? = null
    private var homeTextFlow: MutableStateFlow<String>? = null
    private var songNameFlow: MutableStateFlow<String>? = null

    private var cadenceFlowObserver: Job? = null
    private var homeTextFlowObserver: Job? = null
    private var songNameFlowObserver: Job? = null

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as SRAService.SRABinder
            sraService = binder.getService()
            serviceBound = true
            binding.onOff.text = "ON"

            if (sraService == null) {
                return
            }
            val flows = sraService?.getFlows()
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

            binding.skipSong.isEnabled = serviceBound
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            serviceBound = false
            binding.onOff.text = "OFF"
            cadenceFlowObserver?.cancel(CancellationException())
            homeTextFlowObserver?.cancel(CancellationException())
            songNameFlowObserver?.cancel(CancellationException())
        }
    }

    private fun bindService() {
        Intent(activity!!, SRAService::class.java).also { intent ->
            activity!!.bindService(intent, connection, 0)
        }
    }

    private fun unbindService() {
        activity!!.unbindService(connection)
        serviceBound = false
        binding.onOff.text = "OFF"

        binding.displayCadence.text = "You have to start the Service first"
        binding.textHome.text = ""
        binding.displaySong.text = ""
        binding.skipSong.isEnabled = serviceBound

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
        if (serviceBound) unbindService()
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

        bindService()

        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = /*it*/ "Spotify Running App"
        }

        binding.skipSong.isEnabled = serviceBound

        binding.skipSong.setOnClickListener {
            if (serviceBound) {
                sraService?.skipSong()
            } else {
                Toast.makeText(activity!!, "Service is not active", Toast.LENGTH_SHORT).show()
            }
        }

        binding.onOff.setOnClickListener {
            if (serviceBound) {
                unbindService()
                Intent(activity!!, SRAService::class.java).also {
                    activity!!.stopService(it)
                }
            } else {
                Intent(activity!!, SRAService::class.java).also {
                    activity!!.startService(it)
                }
                bindService()
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

        if (ContextCompat.checkSelfPermission(activity!!,
                Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) requestPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)

        return root
    }

    override fun onResume() {
        super.onResume()
        bindService()
    }

    override fun onPause() {
        super.onPause()
        if (serviceBound) unbindService()
    }
}