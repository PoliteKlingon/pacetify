package com.example.myapplication.ui.home

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.SRAService
import com.example.myapplication.databinding.FragmentHomeBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

class HomeFragment : Fragment()/*, SensorEventListener*/ {

    private var _binding: FragmentHomeBinding? = null

    /*private lateinit var sensorManager: SensorManager

    private var running = false
    private var totalSteps = 0f

    private lateinit var stepsPrevious: Array<Float>
    private var stepsPreviousIdx = 0
    private var stepsPreviousSize = 5
    private lateinit var cadencePrevious: Array<Float>
    private var cadencePreviousIdx = 0
    private var cadencePreviousSize = 10

    lateinit var mainHandler: Handler
    private var seconds: Int = 0*/



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
            binding.onOff.text = "ON" //TODO TOTO DELA PROBLEMY EVIDENTNE

            if (sraService == null) {
                Log.d("asdasd", "ASDASD")
            }
            val flows = sraService?.getFlows() // TODO: Problem?
            if (flows != null) {
                cadenceFlow = flows[0]
                homeTextFlow = flows[1]
                songNameFlow = flows[2]
            }

            Log.d("tre", "service connected")

            cadenceFlowObserver = lifecycleScope.launchWhenStarted {
                cadenceFlow?.collectLatest {
                    binding.displayCadence.text = it
                    Log.d("asd", "1")
                }
            }
            homeTextFlowObserver = lifecycleScope.launchWhenStarted {
                homeTextFlow?.collectLatest {
                    binding.textHome.text = it
                    Log.d("asd", "2")
                }
            }
            songNameFlowObserver = lifecycleScope.launchWhenStarted {
            songNameFlow?.collectLatest {
                    binding.displaySong.text = it
                    Log.d("asd", "3")
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            serviceBound = false
            binding.onOff.text = "OFF"
            cadenceFlowObserver?.cancel(CancellationException())
            homeTextFlowObserver?.cancel(CancellationException())
            songNameFlowObserver?.cancel(CancellationException())
        }
    }

    fun bindService() {
        Intent(activity!!, SRAService::class.java).also { intent ->
            activity!!.bindService(intent, connection, 0)
        }
    }

    override fun onStart() {
        super.onStart()
        bindService()
    }

    fun unbindService() {
        activity!!.unbindService(connection)
        serviceBound = false
        binding.onOff.text = "OFF"

        binding.displayCadence.text = "You have to start the Service first"
        binding.textHome.text = "You have to start the Service first"
        binding.displaySong.text = "You have to start the Service first"

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

    override fun onStop() {
        super.onStop()
        if (serviceBound) unbindService()
    }




    /*private val clock = object : Runnable {
        override fun run() {
            tick()
            mainHandler.postDelayed(this, 1000)
        }
    }*/

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

        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = /*it*/ "Spotify Running App"
        }

        binding.skipSong.setOnClickListener { binding.displaySong.text = "next song" } //TODO
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

        if (cadenceFlow != null) {
            binding.displayCadence.text = "Step cadence: ${cadenceFlow!!.value}" //TODO: vyresit lepe
        } else {
            binding.displayCadence.text = "You have to start the Service first"
        }

        if (homeTextFlow != null) {
            binding.textHome.text = homeTextFlow!!.value //TODO: vyresit lepe
        } else {
            binding.textHome.text = "You have to start the Service first"
        }

        if (songNameFlow != null) {
            binding.displaySong.text = songNameFlow!!.value //TODO: vyresit lepe
        } else {
            binding.displaySong.text = "You have to start the Service first"
        }

        /*sensorManager = activity!!.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        if (ContextCompat.checkSelfPermission(activity!!,
                Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity!!,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 1)
        }

        if (ContextCompat.checkSelfPermission(activity!!,
                Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED) {
            binding.displayCadence.text = "This app will not work without activity recognition enabled. " +
                    "Please add this permission in settings."
        }

        stepsPrevious = Array(stepsPreviousSize) { 0f } //steps recorded for the last ten seconds
        cadencePrevious = Array(cadencePreviousSize) { 0f } //cadence recorded for the last ten seconds

        mainHandler = Handler(Looper.getMainLooper())
        //mainHandler.post(clock)*/

        return root
    }

    /*fun displayCadence(cadence: Int) {
        binding.displayCadence.text = "Step cadence: " + cadence.toString()
    }*/

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        bindService() //TODO: remove?
        /*running = true
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepSensor == null) {
            Toast.makeText(activity, "No sensor detected on this device", Toast.LENGTH_LONG).show()
        } else {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL) //TODO: jiny delay?
        }

        mainHandler.post(clock) // TODO: remove az to bude na pozadi*/
    }

    /*override fun onSensorChanged(p0: SensorEvent?) {
        if (running) {
            totalSteps = p0!!.values[0]
        }
    }*/

    /*override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }*/

    override fun onPause() {
        super.onPause()
        if (serviceBound) unbindService() //TODO: remove?
        /*mainHandler.removeCallbacks(clock) // TODO: remove az to bude na pozadi*/
    }

    /*fun tick() {
        if (stepsPrevious[stepsPreviousIdx] < 1) {
            stepsPrevious = stepsPrevious.map { totalSteps }.toTypedArray()
        }
        seconds++

        stepsPreviousIdx = if (stepsPreviousIdx == stepsPreviousSize - 1) 0 else stepsPreviousIdx + 1
        cadencePreviousIdx = if (cadencePreviousIdx == cadencePreviousSize - 1) 0 else cadencePreviousIdx + 1
        val cadence = (totalSteps - stepsPrevious[stepsPreviousIdx]) * (60 / stepsPreviousSize) //convert to per minute
        stepsPrevious[stepsPreviousIdx] = totalSteps //store totalSteps into history
        cadencePrevious[cadencePreviousIdx] = cadence //store cadence into history

        var maybetext = ""

        if (serviceBound) {
            //maybetext = sraService?.getNumber().toString()
        }

        binding.textHome.text = "clock: " + seconds.toString() +
                "\n totalSteps: " + totalSteps.toString() +
                "\n stepsPrev[0]: " + stepsPrevious[0].toString() +
                "\n stepsPrev[1]: " + stepsPrevious[1].toString() +
                "\n stepsPrev[2]: " + stepsPrevious[2].toString() +
                "\n stepsPrev[3]: " + stepsPrevious[3].toString() +
                "\n stepsPrev[4]: " + stepsPrevious[4].toString() +
                "\n cadencePrev[0]: " + cadencePrevious[0].toString() +
                "\n cadencePrev[1]: " + cadencePrevious[1].toString() +
                "\n cadencePrev[2]: " + cadencePrevious[2].toString() +
                "\n cadencePrev[3]: " + cadencePrevious[3].toString() +
                "\n cadencePrev[4]: " + cadencePrevious[4].toString() +
                "\n cadencePrev[5]: " + cadencePrevious[5].toString() +
                "\n cadencePrev[6]: " + cadencePrevious[6].toString() +
                "\n cadencePrev[7]: " + cadencePrevious[7].toString() +
                "\n cadencePrev[8]: " + cadencePrevious[8].toString() +
                "\n cadencePrev[9]: " + cadencePrevious[9].toString() +
                "\n" + maybetext

        displayCadence(cadencePrevious.average().toInt())
    }*/
}