package com.example.myapplication

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow

class SRAService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager

    private var running = false
    private var totalSteps = 0f

    private var cadence = 0

    private lateinit var stepsPrevious: Array<Float>
    private var stepsPreviousIdx = 0
    private var stepsPreviousSize = 5
    private lateinit var cadencePrevious: Array<Float>
    private var cadencePreviousIdx = 0
    private var cadencePreviousSize = 10

    lateinit var mainHandler: Handler
    private var seconds: Int = 0

    private var cadenceFlow = MutableStateFlow("")
    private var homeTextFlow = MutableStateFlow("")
    private var songNameFlow = MutableStateFlow("")

    inner class SRABinder : Binder() {
        fun getService() = this@SRAService
    }

    private val binder = SRABinder()

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        stepsPrevious = Array(stepsPreviousSize) { 0f } //steps recorded for the last ten seconds
        cadencePrevious = Array(cadencePreviousSize) { 0f } //cadence recorded for the last ten seconds

        mainHandler = Handler(Looper.getMainLooper())

        running = true
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        Log.d("service", "onstartCommand")

        if (stepSensor == null) {
            Toast.makeText(applicationContext, "No sensor detected on this device", Toast.LENGTH_LONG).show()
        } else {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL) //TODO: jiny delay?
        }

        mainHandler.post(clock)

        return START_REDELIVER_INTENT
    }

    /*override fun onResume() {
        running = true
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepSensor == null) {
            Toast.makeText(applicationContext, "No sensor detected on this device", Toast.LENGTH_LONG).show()
        } else {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL) //TODO: jiny delay?
        }

        mainHandler.post(clock) // TODO: remove az to bude na pozadi
    }*/

    override fun onSensorChanged(p0: SensorEvent?) {
        if (running) {
            totalSteps = p0!!.values[0]
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

    /*override fun onPause() {
        mainHandler.removeCallbacks(clock) // TODO: remove az to bude na pozadi
    }*/

    fun tick() {
        if (stepsPrevious[stepsPreviousIdx] < 1) {
            stepsPrevious = stepsPrevious.map { totalSteps }.toTypedArray()
        }
        seconds++

        stepsPreviousIdx = if (stepsPreviousIdx == stepsPreviousSize - 1) 0 else stepsPreviousIdx + 1
        cadencePreviousIdx = if (cadencePreviousIdx == cadencePreviousSize - 1) 0 else cadencePreviousIdx + 1
        val currentCadence = (totalSteps - stepsPrevious[stepsPreviousIdx]) * (60 / stepsPreviousSize) //convert to per minute
        stepsPrevious[stepsPreviousIdx] = totalSteps //store totalSteps into history
        cadencePrevious[cadencePreviousIdx] = currentCadence //store cadence into history

        cadence = cadencePrevious.average().toInt()

        cadenceFlow.value = "Cadence: $cadence steps per minute"
        homeTextFlow.value = "clock: " + seconds.toString() +
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
                "\n cadencePrev[9]: " + cadencePrevious[9].toString()
        songNameFlow.value = ""
    }

    fun getFlows(): Array<MutableStateFlow<String>> {
        return arrayOf(cadenceFlow, homeTextFlow, songNameFlow)
    }

    fun skipSong() {
        //TODO
    }

    private val clock = object : Runnable {
        override fun run() {
            tick()
            mainHandler.postDelayed(this, 1000)
        }
    }
}