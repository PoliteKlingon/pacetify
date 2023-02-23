package com.example.pacetify.data

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.*
import android.util.Log
import android.widget.Toast
import com.example.pacetify.MainActivity
import com.example.pacetify.R
import com.example.pacetify.data.source.database.PacetifyDao
import com.example.pacetify.data.source.database.PacetifyDatabase
import com.example.pacetify.data.source.preferenceFiles.SettingsPreferenceFile
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.Runnable
import kotlin.math.abs
import kotlin.random.Random


/**
 * This is the service that contains the running functionality - calculates cadence and controls
 * the Spotify playback.
 */
class PacetifyService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    // We need the audio manager for the fake crossfade
    private lateinit var audioManager: AudioManager

    private var stepSensorRunning = false
    private var totalSteps = 0f

    private var cadence = 0 // average from last couple of seconds
    private var currentCadence = 0f // exact current cadence

    // step count history + size and current idx for storing
    private lateinit var stepsPrevious: Array<Float>
    private var stepsPreviousIdx = 0
    private var stepsPreviousSize = 5
    // the same for cadence
    private lateinit var cadencePrevious: Array<Float>
    private var cadencePreviousIdx = 0
    private var cadencePreviousSize = 10

    // handler for our clock
    lateinit var mainHandler: Handler

    // flows for passing info to the UI
    private var cadenceFlow = MutableStateFlow("")
    private var homeTextFlow = MutableStateFlow("")
    private var songNameFlow = MutableStateFlow("")

    // Spotify app remote
    private val CLIENT_ID = "29755c71ec3a4765aec6d780e0b71214"
    private val REDIRECT_URI = "com.example.pacetify://callback"
    private var mSpotifyAppRemote: SpotifyAppRemote? = null

    private var dao: PacetifyDao? = null
    private var settingsFile: SettingsPreferenceFile? = null

    // variables for the user's settings to be loaded into
    private var motivate = false
    private var rest = false
    private var restTime = 20

    private val MOTIVATE_ADDITION = 3 // bpm to add if user wants to be motivated
    private val RUNNING_THRESHOLD = 80 // lowest bpm that is considered to be running
    private val SONG_MINIMAL_SECONDS = 15 // lowest amount of seconds to be played from a song before skipping
    private val INITIAL_CADENCE = 150 // the bpm of the first played song  //TODO into settings?
    private val FAKE_CROSSFADE_DURATION_MS: Long = 1000 // duration of fake volume crossfade in ms
    private var lastRunningBpm = INITIAL_CADENCE // used for rest functionality
    private var wasResting = false // information if the user was resting during the last song
    private var currentRestingTime = restTime
    private var currentlyResting = false

    private var songs: Array<Song> = arrayOf()

    private var playerStateSubscription: Subscription<PlayerState>? = null
    private var timeToSongEnd: Long = 15
    private var timePlayedFromSong = 0
    private var currentBpm = 0 // the bpm of the current song
    private var notificationManager: NotificationManager? = null
    private var notification: Notification.Builder? = null

    private var shouldStartPlaying = true

    inner class PacetifyBinder : Binder() {
        fun getService() = this@PacetifyService
    }

    private val binder = PacetifyBinder()

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    fun startTicking() {
        mainHandler.post(clock)
    }

    fun stopTicking() {
        mainHandler.removeCallbacks(clock)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        makeServiceForeground()

        shouldStartPlaying = intent?.getBooleanExtra("tick", true) ?: true

        sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        stepsPrevious = Array(stepsPreviousSize) { 0f } //steps recorded for the last ten seconds
        cadencePrevious = Array(cadencePreviousSize) { 0f } //cadence recorded for the last ten seconds

        mainHandler = Handler(Looper.getMainLooper()) // create a handler for our clock

        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        Log.d("service", "Pacetify service started")

        if (stepSensor == null) {
            Toast.makeText(applicationContext, "No sensor detected on this device", Toast.LENGTH_LONG).show()
            this.stopSelf()
        } else {
            stepSensorRunning = true
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        dao = PacetifyDatabase.getInstance(this).pacetifyDao
        loadSongs()

        settingsFile = SettingsPreferenceFile.getInstance(this)
        loadSettings()

        connectToSpotifyAppRemote()

        // if the service gets destroyed by the system, restart it
        return START_REDELIVER_INTENT
    }

    private fun makeServiceForeground() {
        // We have to do this so Android Oreo and higher does not kill the service.

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val mChannel = NotificationChannel("PacetifyChannel", "PacetifyChannel", NotificationManager.IMPORTANCE_LOW)
            mChannel.description = "This is an Pacetify notification channel"
            // Register the channel with the system
            notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager!!.createNotificationChannel(mChannel)

            // this intent is for the notification to be able to open our app on click
            val pendingIntent: PendingIntent =
                Intent(this, MainActivity::class.java).let { notificationIntent ->
                    PendingIntent.getActivity(
                        this, 0, notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE
                    )
                }

            notification =
                Notification.Builder(this,"PacetifyChannel")
                    .setContentTitle("Pacetify service is running")
                    .setContentText("Let's run!")
                    .setContentIntent(pendingIntent)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .also {
                        startForeground(1, it.build())
                    }
        }
    }

    private fun connectToSpotifyAppRemote() {
        // Set the connection parameters
        val connectionParams = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true)
            .setRequiredFeatures(listOf())
            .build()

        // Perform the connection
        SpotifyAppRemote.connect(this, connectionParams,
            object : Connector.ConnectionListener {
                override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                    mSpotifyAppRemote = spotifyAppRemote
                    Log.d("PacetifyService", "Connected!")

                    onSpotifyAppRemoteConnected()
                }

                override fun onFailure(throwable: Throwable) {
                    Log.e("PacetifyService", throwable.message, throwable)

                    Toast.makeText(
                        applicationContext,
                        "Something went wrong while trying to connect to Spotify, please try again",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    fun onSpotifyAppRemoteConnected() {
        //send the current song info into its flow - subscribe to the player state
        playerStateSubscription = mSpotifyAppRemote?.playerApi?.subscribeToPlayerState()
            ?.setEventCallback { event ->
                val track = event.track
                songNameFlow.value = "${track.name}\n ${track.artist.name}\n ${track.album.name}"
                timeToSongEnd = (track.duration - event.playbackPosition) / 1000
            }

        if (shouldStartPlaying) {
            //play first song
            playSong(chooseSong(INITIAL_CADENCE))
            wasResting = true

            // If the user does not have crossfade enabled, we advice them to do so
            mSpotifyAppRemote?.playerApi?.crossfadeState?.setResultCallback { ev ->
                if (!ev.isEnabled)
                    Toast.makeText(
                        applicationContext,
                        "Enabling crossfade in spotify settings leads to better experience",
                        Toast.LENGTH_LONG
                    ).show()
            }

            startTicking()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pauseSong()
        playerStateSubscription?.cancel()
        stopTicking()
        SpotifyAppRemote.disconnect(mSpotifyAppRemote);
        Log.d("PacetifyService", "disconnected from Spotify")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) stopForeground(STOP_FOREGROUND_DETACH)
    }

    override fun onSensorChanged(p0: SensorEvent?) {
        if (stepSensorRunning)
            totalSteps = p0!!.values[0]
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}

    private fun calculateCadence() {
        if (stepsPrevious[stepsPreviousIdx] < 1) {
            stepsPrevious = stepsPrevious.map { totalSteps }.toTypedArray() // quicker initialization
        }

        //update current indices
        stepsPreviousIdx = if (stepsPreviousIdx == stepsPreviousSize - 1) 0 else stepsPreviousIdx + 1
        cadencePreviousIdx = if (cadencePreviousIdx == cadencePreviousSize - 1) 0 else cadencePreviousIdx + 1
        currentCadence = (totalSteps - stepsPrevious[stepsPreviousIdx]) * (60 / stepsPreviousSize) //convert to per minute
        stepsPrevious[stepsPreviousIdx] = totalSteps //store totalSteps into history
        cadencePrevious[cadencePreviousIdx] = currentCadence //store cadence into history

        cadence = cadencePrevious.average().toInt()
    }

    private fun updateNotification() {
        if (notification != null) {
            notification!!.setContentText("Your pace is $cadence")
                .also {
                    notificationManager?.notify(1, it.build())
                }
        }
    }

    // A single tick of the clock - this function is executed every second
    fun tick() {
        calculateCadence()
        cadenceFlow.value = "Cadence: $cadence steps per minute"

        updateNotification()

        // We are running, so we remember the current bpm
        if (currentBpm > RUNNING_THRESHOLD) lastRunningBpm = currentBpm

        timePlayedFromSong++
        timeToSongEnd--
        if (currentlyResting) currentRestingTime--

        // here come all the reasons why should a song be skipped
        // we always deal with it in some manner, but it always leads to queueing a new song
        // and we only want that to happen once, therefore it is an "else if" scenario

        if (rest && !isRunning() && !currentlyResting && !wasResting) onStoppedRunning()

        else if (isRunning() && currentlyResting) onStartedRunning()

        else if (isRunning() && currentCadence > RUNNING_THRESHOLD
            && timePlayedFromSong > SONG_MINIMAL_SECONDS
            && (abs(cadence - currentBpm) > 5)) //TODO maybe some smarter way? - more consistent speed increase leads to song change
            skipSong()

        else if (timeToSongEnd <= 10) queueSong()

        else if (currentRestingTime < 0) { // resting over
            Log.d("PacetifyService", "resting over!")
            skipSong()
            currentlyResting = false
            wasResting = true
            currentRestingTime = restTime
        }

        updateHomeTextFlow()
    }

    private fun updateHomeTextFlow() {
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

    private fun onStartedRunning() {
        Log.d("PacetifyService", "started running")
        currentlyResting = false
        wasResting = true
        skipSong()
    }

    private fun onStoppedRunning() {
        Log.d("PacetifyService", "stopped running")
        currentlyResting = true
        currentRestingTime = restTime
        skipSong()
    }

    private fun calculateNextSongBpm(): Int {
        return if (!isRunning()) {
            if (rest && currentRestingTime > 0){
                // return random resting bpm, but not too low - then it would have a tendency to
                // always choose the slowest song available
                Random.nextInt(RUNNING_THRESHOLD - 30, RUNNING_THRESHOLD)
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
        crossfadeSkip()
    }

    fun playSong(song: Song?) {
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

    private fun pauseSong() {
        mSpotifyAppRemote?.playerApi?.pause()
    }

    // We fake the crossfade skip by lowering the volume, skipping, and then turning it up again.
    private fun crossfadeSkip() { //TODO ensure this is not called multiple times simultaneously
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val crossfadeDurationMs = FAKE_CROSSFADE_DURATION_MS
        val crossfadeStepMs = crossfadeDurationMs / 10

        var i = 10

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (i >= 0) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    ((i / 10f) * originalVolume).toInt(),
                    0
                )
                delay(crossfadeStepMs)
                i--
            }

            mSpotifyAppRemote?.playerApi?.skipNext()
            mSpotifyAppRemote?.playerApi?.seekTo(1000 * 10)

            while (i <= 10) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    ((i / 10f) * originalVolume).toInt(),
                    0
                )
                delay(crossfadeStepMs)
                i++
            }
        }
    }

    // this is called by the activity when the playlists have been changed
    fun notifyPlaylistsChanged() {
        loadSongs()
    }

    // this is called by the activity when the settings have been changed
    fun notifySettingsChanged() {
        loadSettings()
    }

    private fun loadSettings() {
        if (settingsFile != null) {
            motivate = settingsFile!!.motivate
            rest = settingsFile!!.rest
            restTime = settingsFile!!.restTime
            currentRestingTime = if (rest) restTime else 0
        }
    }

    private fun loadSongs() {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            if (dao != null) songs = dao!!.getSongs()
        }
    }

    private val clock = object : Runnable {
        override fun run() {
            mainHandler.postDelayed(this, 1000)
            tick()
        }
    }

    // choosing the song based on the desired bpm
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