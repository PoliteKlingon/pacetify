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
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
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
    private val MOTIVATE_ADDITION = 3 // bpm to add if user wants to be motivated
    private val RUNNING_THRESHOLD = 80 // lowest bpm that is considered to be running
    private val SONG_MINIMAL_SECONDS = 15 // lowest amound of seconds to be played from a song before skipping
    private var lastRunningBpm = 140 // used for rest functionality
    private var wasResting = false
    private var currentRestingTime = restTime
    private var currentlyResting = false

    private var songs: Array<Song> = arrayOf()

    private var playerStateSubscription: Subscription<PlayerState>? = null
    private var timeToSongEnd: Long = 15
    private var timePlayedFromSong = 0
    private var currentBpm = 0

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

        Log.d("service", "onStartCommand")

        if (stepSensor == null) {
            Toast.makeText(applicationContext, "No sensor detected on this device", Toast.LENGTH_LONG).show()
        } else {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        dao = SRADatabase.getInstance(this).SRADao
        loadSongs()

        sharedPref = this.getSharedPreferences("settings", Context.MODE_PRIVATE)
        loadSettings()

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
                    Log.d("SRAService", "Connected!")

                    connected()
                }

                override fun onFailure(throwable: Throwable) {
                    Log.e("SRAService", throwable.message, throwable)

                    Toast.makeText(
                        applicationContext,
                        "Something went wrong while trying to connect to Spotify, please try again",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })

        return START_REDELIVER_INTENT
    }

    fun connected() {
        //display current song
        playerStateSubscription = mSpotifyAppRemote?.playerApi?.subscribeToPlayerState()
            ?.setEventCallback { event ->
                val track = event.track
                songNameFlow.value = "${track.name}\n ${track.artist.name}\n ${track.album.name}"
                timeToSongEnd = (track.duration - event.playbackPosition) / 1000
            }

        //play first song
        playSong(chooseSong(140))
        wasResting = true

        //TODO enable crossfade somehow?
        mSpotifyAppRemote?.playerApi?.crossfadeState?.setResultCallback {
                ev -> if (!ev.isEnabled)
                    Toast.makeText(
                        applicationContext,
                        "Enabling crossfade in spotify settings leads to better experience",
                        Toast.LENGTH_LONG
                    ).show()
        }

        mainHandler.post(clock) //start ticking
        }

    override fun onDestroy() {
        super.onDestroy()
        playerStateSubscription?.cancel()
        SpotifyAppRemote.disconnect(mSpotifyAppRemote);
        Log.d("SRAService", "disconnected from Spotify")
    }

    override fun onSensorChanged(p0: SensorEvent?) {
        if (running)
            totalSteps = p0!!.values[0]
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}

    fun tick() {
        if (stepsPrevious[stepsPreviousIdx] < 1) {
            stepsPrevious = stepsPrevious.map { totalSteps }.toTypedArray()
        }

        stepsPreviousIdx = if (stepsPreviousIdx == stepsPreviousSize - 1) 0 else stepsPreviousIdx + 1
        cadencePreviousIdx = if (cadencePreviousIdx == cadencePreviousSize - 1) 0 else cadencePreviousIdx + 1
        val currentCadence = (totalSteps - stepsPrevious[stepsPreviousIdx]) * (60 / stepsPreviousSize) //convert to per minute
        stepsPrevious[stepsPreviousIdx] = totalSteps //store totalSteps into history
        cadencePrevious[cadencePreviousIdx] = currentCadence //store cadence into history

        cadence = cadencePrevious.average().toInt()

        if (currentBpm > RUNNING_THRESHOLD) lastRunningBpm = currentBpm

        cadenceFlow.value = "Cadence: $cadence steps per minute"

        timePlayedFromSong++
        timeToSongEnd--

        if (!isRunning() && !currentlyResting && !wasResting) stoppedRunning()

        if (isRunning() && currentlyResting) startedRunning()

        if (isRunning() && timePlayedFromSong > SONG_MINIMAL_SECONDS
            && (abs(cadence - currentBpm) > 5)) //TODO maybe some smarter way? - more consistent speed increase leads to song change
            skipSong()

        if (timeToSongEnd <= 10) queueSong()

        if (currentlyResting) currentRestingTime--

        if (currentRestingTime < 0) { // resting over
            skipSong()
            currentlyResting = false
            wasResting = true
            currentRestingTime = restTime
        }

        homeTextFlow.value =
                "currentBpm: $currentBpm\n" +
                "wasResting: $wasResting\n" +
                "lastRunningBpm: $lastRunningBpm\n" +
                "timePlayedFromSong: $timePlayedFromSong\n" +
                "timeToSongEnd: $timeToSongEnd\n\n" +
                if (currentlyResting) "Resting: $currentRestingTime s left" else ""
    }

    private fun isRunning(): Boolean {
        return cadence > RUNNING_THRESHOLD
    }

    private fun startedRunning() {
        currentlyResting = false
        wasResting = true
        skipSong()
    }

    private fun stoppedRunning() {
        currentlyResting = true
        currentRestingTime = restTime
        skipSong()
    }

    private fun calculateNextSongBpm(): Int {
        return if (!isRunning()) {
            if (rest && currentRestingTime > 0){
                Random.nextInt(RUNNING_THRESHOLD - 40, RUNNING_THRESHOLD)
            } else lastRunningBpm
        } else {
            cadence + if (motivate) MOTIVATE_ADDITION else 0
        }
    }

    fun getFlows(): Array<MutableStateFlow<String>> {
        return arrayOf(cadenceFlow, homeTextFlow, songNameFlow)
    }

    fun skipSong() {
        queueSong()
        mSpotifyAppRemote?.playerApi?.skipNext()
        mSpotifyAppRemote?.playerApi?.seekTo(1000 * 10) //TODO crossfade
    }

    private fun playSong(song: Song?) {
        if (song == null) {
            Toast.makeText(applicationContext, "You must add some playlist first", Toast.LENGTH_LONG).show()
            return
        }
        mSpotifyAppRemote?.playerApi?.play(song.uri)
        currentBpm = song.bpm
    }

    private fun queueSong() {
        var nextBpm = calculateNextSongBpm()
        if (!isRunning() && wasResting) {
            nextBpm = lastRunningBpm //do not rest again when user was just resting
        }
        val song = chooseSong(nextBpm)

        if (song == null) {
            Toast.makeText(applicationContext, "You must add some playlist first", Toast.LENGTH_LONG).show()
            return
        }

        mSpotifyAppRemote?.playerApi?.queue(song.uri)
        currentBpm = song.bpm

        timePlayedFromSong = 0
        if (wasResting) wasResting = false
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
            restTime = (sharedPref!!.getInt("progress", 20) + 1) * 10
            currentRestingTime = restTime
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
}