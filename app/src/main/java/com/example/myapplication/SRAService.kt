package com.example.myapplication

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch


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

    private val CLIENT_ID = "29755c71ec3a4765aec6d780e0b71214"
    private val REDIRECT_URI = "com.example.myapplication://callback" //TODO??
    private var mSpotifyAppRemote: SpotifyAppRemote? = null

    private var dao: SRADao? = null
    private var sharedPref: SharedPreferences? = null

    private var motivate = false
    private var rest = false
    private var restTime = 20

    private var songs: List<Song> = listOf()

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

        dao = SRADatabase.getInstance(this).SRADao
        loadSongs()

        sharedPref = this.getSharedPreferences("settings", Context.MODE_PRIVATE)
        loadSettings()

        mainHandler.post(clock)

        // SPOTIFY:
        // Set the connection parameters
        val connectionParams = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true)
            .setRequiredFeatures(listOf())
            .build()

        SpotifyAppRemote.connect(this, connectionParams,
            object : Connector.ConnectionListener {
                override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                    mSpotifyAppRemote = spotifyAppRemote
                    Log.d("SRAService", "Connected! Yay!")

                    // Now you can start interacting with App Remote
                    connected()
                }

                override fun onFailure(throwable: Throwable) {
                    Log.e("SRAService", throwable.message, throwable)

                    // Something went wrong when attempting to connect! Handle errors here
                }
            })


        return START_REDELIVER_INTENT
    }

    fun connected() {
        mSpotifyAppRemote?.playerApi?.play("spotify:playlist:37i9dQZF1DX2sUQwD7tbmL");
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            homeTextFlow.value = dao?.getPlaylists().toString()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SpotifyAppRemote.disconnect(mSpotifyAppRemote);
        Log.d("SRAService", "disconnected from Spotify")
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
        /*homeTextFlow.value = "clock: " + seconds.toString() +
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
        songNameFlow.value = ""*/
    }

    fun getFlows(): Array<MutableStateFlow<String>> {
        return arrayOf(cadenceFlow, homeTextFlow, songNameFlow)
    }

    fun skipSong() {
        mSpotifyAppRemote?.playerApi?.skipNext()
    }

    fun getSongsFromPlaylist(playlist: Playlist): List<Song> {
        return listOf()//TODO
    }

    fun isValidUri(uri: String) : Boolean {
        return true //TODO
    }

    fun notifyPlaylistsChanged() {
        loadSongs()
    }

    fun notifySettingsChanged() {
        loadSettings()
    }

    private fun loadSettings() {
        if (sharedPref != null) {
            motivate = sharedPref!!.getBoolean("motivate", false)
            rest = sharedPref!!.getBoolean("rest", false)
            restTime = sharedPref!!.getInt("progress", 20)
        }
    }

    private fun loadSongs() {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            if (dao != null) songs = dao!!.getSongs()
        }
    }

    private val clock = object : Runnable {
        override fun run() {
            tick()
            mainHandler.postDelayed(this, 1000)
        }
    }

    //TODO hlavni funkcionalita zde
}