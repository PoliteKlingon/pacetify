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
import android.webkit.URLUtil
import android.widget.Toast
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONException
import java.io.IOException
import kotlin.random.Random


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
    private val MOTIVATE_ADDITION = 5 //bpm to add if user wants to be motivated

    private var songs: Array<Song> = arrayOf()

    private var playerStateSubscription: Subscription<PlayerState>? = null
    private var timeToSongEnd: Long = 15
    private var timePlayedFromSong = 0

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

        // SPOTIFY SDK:
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

                    connected()
                }

                override fun onFailure(throwable: Throwable) {
                    Log.e("SRAService", throwable.message, throwable)

                    // TODO Something went wrong when attempting to connect! Handle errors here
                }
            })

        return START_REDELIVER_INTENT
    }

    fun connected() {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            homeTextFlow.value = dao?.getPlaylists().toString()
        }

        //display current song
        playerStateSubscription = mSpotifyAppRemote?.playerApi?.subscribeToPlayerState()
            ?.setEventCallback { event ->
                val track = event.track
                songNameFlow.value = "${track.name}\n ${track.artist.name}\n ${track.album.name}"
                timeToSongEnd = track.duration - event.playbackPosition
            }

        //play first song
        playSong(chooseSong(140))
        //TODO crossfade? no way to set it apparently
        }

    override fun onDestroy() {
        super.onDestroy()
        playerStateSubscription?.cancel()
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
        val cadenceBefore = cadencePrevious.average().toInt() //last cadence
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

        if (timeToSongEnd <= 10)
            queueSong(chooseSong(cadence + if (motivate) MOTIVATE_ADDITION else 0))

        timePlayedFromSong++

        if (timePlayedFromSong > 10 && cadence + 5 >= cadenceBefore) { //TODO smarter way - more consistent speed increase leads to song change also compare with current bpm, not cadence before?
            skipSong()
            timePlayedFromSong = 0
        }
    }

    fun getFlows(): Array<MutableStateFlow<String>> {
        return arrayOf(cadenceFlow, homeTextFlow, songNameFlow)
    }

    fun skipSong() {
        queueSong(chooseSong(cadence + if (motivate) MOTIVATE_ADDITION else 0))
        mSpotifyAppRemote?.playerApi?.skipNext()
    }

    private fun playSong(song: Song?) {
        if (song == null) {
            Toast.makeText(applicationContext, "You must add some playlist first", Toast.LENGTH_LONG).show()
            return
        }
        mSpotifyAppRemote?.playerApi?.play(song.uri)
    }

    private fun queueSong(song: Song?) {
        if (song == null) {
            Toast.makeText(applicationContext, "You must add some playlist first", Toast.LENGTH_LONG).show()
            return
        }
        mSpotifyAppRemote?.playerApi?.queue(song.uri)
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

    private fun chooseSong(bpm: Int): Song? {
        if (songs.isEmpty()) return null
        val songIndex = binarySearchSongIndex(bpm, 0, songs.size - 1)
        var startIndex = songIndex
        var stopIndex = songIndex

        // considering the supposed number of songs with the same index is small enough for
        // the linear search to be faster on average than a binary search for both bounds
        while (startIndex > 0 && songs[startIndex - 1].bpm == bpm) {
            startIndex--
        }
        while (stopIndex < songs.size - 1 && songs[stopIndex + 1].bpm == bpm) {
            stopIndex++
        }

        return songs[Random.nextInt(startIndex, stopIndex + 1)] //stop is exclusive
    }

    private fun binarySearchSongIndex(targetBpm: Int, start: Int, stop: Int): Int {
        if (songs[start].bpm == targetBpm) return start
        if (songs[stop].bpm == targetBpm) return stop
        if (start >= stop) return start

        val mid = (start + stop) / 2
        return if (songs[mid].bpm < targetBpm) {
            binarySearchSongIndex(targetBpm, mid + 1, stop)
        } else {
            binarySearchSongIndex(targetBpm, start, mid)
        }
    }

    //TODO hlavni funkcionalita
        //TODO na zacatku pustit nejakou generic
        //TODO zaradit pokud zmena bpm nebo jsem na konci songu (duration - progress v SDK api)
        //TODO vybrat song se spravnym bpm
        //TODO skip pripadne
        //TODO asi to bude v ticku, ne? idk
        //TODO fake crossfade pomoci hlasitosti?
}