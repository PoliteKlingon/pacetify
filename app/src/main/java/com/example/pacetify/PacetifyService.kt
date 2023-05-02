package com.example.pacetify

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.*
import android.os.PowerManager.WakeLock
import android.util.Log
import android.widget.Toast
import com.example.pacetify.data.Song
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
import kotlinx.coroutines.sync.Mutex
import java.lang.Runnable
import kotlin.math.abs
import kotlin.random.Random


/**
 * This is the service that contains the running functionality - calculates cadence and controls
 * the Spotify playback.
 *
 * author: Jiří Loun
 */
class PacetifyService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    // I need the audio manager for the fake crossfade
    private lateinit var audioManager: AudioManager

    private var isCrossfadeEnabled = false

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
    private lateinit var mainHandler: Handler
    private var ticking = false

    // flows for passing info to the UI
    private var cadenceFlow = MutableStateFlow("")
    private var infoFlow = MutableStateFlow("")
    private var songNameFlow = MutableStateFlow("")
    private var songDescriptionFlow = MutableStateFlow("")

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

    private val RUNNING_THRESHOLD = 120 // lowest bpm that is considered to be running
    private val WALKING_THRESHOLD = 70 // lowest bpm that is considered to be walking
    private var runningThreshold = RUNNING_THRESHOLD // lowest non-resting bpm for current mode


    private val MOTIVATE_ADDITION = 3 // bpm to add if user wants to be motivated
    private val SONG_MINIMAL_SECONDS = 15 // lowest amount of seconds to be played from a song before skipping
    private val INITIAL_CADENCE = 150 // the bpm of the first played song  //TODO into settings?
    private val FAKE_CROSSFADE_DURATION_MS: Long = 1000 // duration of fake volume crossfade in ms
    private val crossfadeMutex = Mutex() // mutex for the crossfade locking
    private var lastRunningBpm = INITIAL_CADENCE // used for rest functionality
    private var wasResting = false // information if the user was resting during the last song
    private var currentRestingTime = restTime
    private var currentlyResting = false

    private var songs: Array<Song> = arrayOf()

    private var playerStateSubscription: Subscription<PlayerState>? = null
    private var timeToSongEnd: Long = 15
    private var timePlayedFromSong = 0
    private var currentSong: Song? = null
    private var notificationManager: NotificationManager? = null
    private var notification: Notification.Builder? = null

    private var shouldStartPlaying = true

    // this mutex is for handling the concurrency of reloading songs and picking a song
    private var songsLoadingMutex = Mutex()

    // this lock keeps the CPU awake even when the screen is off
    private lateinit var wakeLock: WakeLock

    inner class PacetifyBinder : Binder() {
        fun getService() = this@PacetifyService
    }

    private val binder = PacetifyBinder()

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    fun startTicking() {
        if (!ticking) {
            mainHandler.post(clock)
            ticking = true
        }
    }

    fun stopTicking() {
        if (ticking) {
            mainHandler.removeCallbacks(clock)
            ticking = false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "SKIP_SONG") {
            orderSkipSong()
            return START_REDELIVER_INTENT
        }
        Log.d("service", "Pacetify service started")

        shouldStartPlaying = intent?.getBooleanExtra("tick", true) ?: true
        // I want the service to be foreground (and create a notification) only when it has been
        // started to run for a long time, not when it has been started to play individual songs
        // is the Songs dialogFragment - in that case, it will be temporary
        if (shouldStartPlaying) makeServiceForeground()

        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PacetifyService::Wakelock").apply {
                    acquire()
                }
            }

        sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        stepsPrevious = Array(stepsPreviousSize) { 0f } //steps recorded for the last ten seconds
        cadencePrevious = Array(cadencePreviousSize) { 0f } //cadence recorded for the last ten seconds

        mainHandler = Handler(this.mainLooper) // create a handler for our clock

        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor == null) {
            Toast.makeText(applicationContext, "No sensor detected on this device", Toast.LENGTH_LONG).show()
            shouldStartPlaying = false
            infoFlow.value = "No sensor detected on this device"
        } else {
            stepSensorRunning = true
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        dao = PacetifyDatabase.getInstance(this).pacetifyDao
        loadSongs(restartTicking = false)

        settingsFile = SettingsPreferenceFile.getInstance(this)
        loadSettings()

        connectToSpotifyAppRemote()

        // if the service gets destroyed by the system, restart it
        return START_REDELIVER_INTENT
    }

    private fun makeServiceForeground() {
        // I have to do this so Android Oreo and higher does not kill the service.

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val mChannel = NotificationChannel("Pacetify", "Pacetify", NotificationManager.IMPORTANCE_HIGH)
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

            // this intent is for the notification button to be able to skip a song
            val pendingSkipIntent: PendingIntent =
                Intent(this, PacetifyService::class.java).let { skipIntent ->
                    skipIntent.action = "SKIP_SONG"
                    PendingIntent.getService(
                        this, 0, skipIntent,
                        PendingIntent.FLAG_IMMUTABLE
                    )
                }

            // Create the skip song action
            val skipAction = Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_skip),
                "Skip Song",
                pendingSkipIntent
            ).build()

            notification =
                Notification.Builder(this,"Pacetify")
                    .setContentTitle("Pacetify service is running")
                    .setContentText("Let's run!")
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .addAction(skipAction)
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
                    shouldStartPlaying = false
                    infoFlow.value = "Something went wrong while trying to connect to Spotify"
                }
            })
    }

    fun onSpotifyAppRemoteConnected() {
        // When I start the service only to play individual songs in the Song dialogFragment,
        // I do not wish to execute any of this
        if (!shouldStartPlaying) return

        //send the current song info into its flow - subscribe to the player state
        playerStateSubscription = mSpotifyAppRemote?.playerApi?.subscribeToPlayerState()
            ?.setEventCallback { event ->
                val track = event.track
                songDescriptionFlow.value = "${track.artist.name}\n ${track.album.name}"
                songNameFlow.value = track.name
                timeToSongEnd = (track.duration - event.playbackPosition) / 1000
            }

        // If the user does not have crossfade enabled, I advice them to do so
        mSpotifyAppRemote?.playerApi?.crossfadeState?.setResultCallback { ev ->
            if (ev.isEnabled)
                isCrossfadeEnabled = true
            else
                Toast.makeText(
                    applicationContext,
                    "Enabling crossfade in the Spotify app settings leads to a better experience",
                    Toast.LENGTH_LONG
                ).show()
        }

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            // play the first song
            songsLoadingMutex.lock()
            val songIdx = chooseSongIdx(INITIAL_CADENCE)
            if (songIdx == -1) {
                songsLoadingMutex.unlock()
                return@launch
            }
            val song = songs[songIdx]
            songsLoadingMutex.unlock()
            playSong(song)
            currentSong = song
            wasResting = true

            startTicking()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock.release()
        pauseSong()
        playerStateSubscription?.cancel()
        stopTicking()
        SpotifyAppRemote.disconnect(mSpotifyAppRemote)
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
            notification!!.setContentText("Your cadence is $cadence")
                .also {
                    notificationManager?.notify(1, it.build())
                }
        }
    }

    private var skipNextTime = false

    fun orderSkipSong() {
        skipNextTime = true
    }

    // A single tick of the clock - this function is executed every second
    fun tick() {
        Log.d("Service", "tick")

        if (skipNextTime) {
            skipNextTime = false
            skipSong()
            return
        }
        calculateCadence()
        cadenceFlow.value = "Cadence: $cadence"

        updateNotification()

        // User is running, so I remember the current bpm
        if (currentSong != null && currentSong!!.bpm > runningThreshold) lastRunningBpm = currentSong!!.bpm

        timePlayedFromSong++
        timeToSongEnd--
        if (currentlyResting) currentRestingTime--

        // here come all the reasons why should a song be skipped
        // I always deal with it in some specific manner, but it always leads to selecting a new
        // song and I only want that to happen once, therefore it is an "else if" scenario

        if (rest && !isRunning() && !currentlyResting && !wasResting) onStoppedRunning()

        else if (isRunning() && currentlyResting) onStartedRunning()

        else if (isRunning() && currentCadence > runningThreshold
            && timePlayedFromSong > SONG_MINIMAL_SECONDS
            && currentSong != null && (abs(cadence - currentSong!!.bpm) > 4)) //TODO tweak this according to test
            skipSong()

        else if (timeToSongEnd <= 10) {
            // Here we are at the end of the song, so if the user has crossfade enabled in their app,
            // I simply queue the next song and let Spotify handle the crossfade. Otherwise, I do
            // our fake crossfade.
            if (isCrossfadeEnabled) findNextSong(queue = true)
            else skipSong()
        }

        else if (currentRestingTime < 0) { // resting over
            Log.d("PacetifyService", "resting over!")
            skipSong()
            currentlyResting = false
            wasResting = true
            currentRestingTime = restTime
        }

        updateInfoFlow()
    }

    private fun updateInfoFlow() {
        infoFlow.value =
            "Current song's BPM: ${currentSong?.bpm}\n" +
                    /*"wasResting: $wasResting\n" +
                    "lastRunningBpm: $lastRunningBpm\n" +
                    "timePlayedFromSong: $timePlayedFromSong\n" +
                    "timeToSongEnd: $timeToSongEnd\n\n" +*/
                    if (currentlyResting) "Resting: $currentRestingTime s left" else ""
    }

    private fun isRunning(): Boolean {
        return cadence > runningThreshold
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
                Random.nextInt(runningThreshold - 30, runningThreshold)
            } else lastRunningBpm
        } else {
            cadence + if (motivate) MOTIVATE_ADDITION else 0
        }
    }

    fun getFlows(): Array<MutableStateFlow<String>> {
        return arrayOf(cadenceFlow, infoFlow, songNameFlow, songDescriptionFlow)
    }

    private fun skipSong() {
        findNextSong() // select the next song and save it in the currentSong variable
        crossfadeSkip() // play the currentSong with crossfade
    }

    fun playSong(song: Song) {
        mSpotifyAppRemote?.playerApi?.play(song.uri)
        currentSong = song
    }

    // This function finds the next song to be played and saves it into the currentSong variable.
    // Additionally, it can enqueue the song in Spotify if the argument 'queue' is set to true.
    private fun findNextSong(queue: Boolean = false) {
        var nextBpm = calculateNextSongBpm()
        if (!isRunning() && wasResting) {
            nextBpm = lastRunningBpm //do not rest again when user was just resting
        }

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            songsLoadingMutex.lock()
            var songIdx = chooseSongIdx(nextBpm)
            if (songIdx == -1) {
                songsLoadingMutex.unlock()
                return@launch
            }
            // if there is more than one song, do not play the same song twice
            if (currentSong?.uri == songs[songIdx].uri) {
                if (songIdx < songs.size - 1) songIdx++
                else if (songIdx > 0) songIdx--
            }
            val song = songs[songIdx]
            songsLoadingMutex.unlock()

            if (queue) mSpotifyAppRemote?.playerApi?.queue(song.uri)
            currentSong = song
        }

        timePlayedFromSong = 0
        if (wasResting) wasResting = false
    }

    fun pauseSong() {
        mSpotifyAppRemote?.playerApi?.pause()
    }

    // I fake the crossfade skip by lowering the volume, skipping, and then turning it up again.
    private fun crossfadeSkip() {
        // using the crossfadeSkipMutex ensures that there are no concurrent crossfades
        // - that would cause unwanted behaviour
        // if I am already skipping, I do not wish to attempt to skip again
        if (crossfadeMutex.isLocked) return

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            // locking means that I am currently skipping
            crossfadeMutex.lock()

            val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val crossfadeDurationMs = FAKE_CROSSFADE_DURATION_MS
            val crossfadeStepMs = crossfadeDurationMs / 10

            var i = 10

            while (i >= 0) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    ((i / 10f) * originalVolume).toInt(),
                    0
                )
                delay(crossfadeStepMs)
                i--
            }

            if (currentSong != null) playSong(currentSong!!)
            // wait for the skip to execute
            delay(250)
            // start the song on the fifteenth second to avoid slow song starts
            mSpotifyAppRemote?.playerApi?.seekTo(1000 * 15)

            while (i <= 10) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    ((i / 10f) * originalVolume).toInt(),
                    0 //AudioManager.FLAG_SHOW_UI
                )
                delay(crossfadeStepMs)
                i++
            }
            // I have finished the skipping
            crossfadeMutex.unlock()
        }
    }

    // this is called by the activity when the playlists have been changed
    fun notifyPlaylistsChanged(restartTicking: Boolean = true) {
        loadSongs(restartTicking = restartTicking)
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
            runningThreshold = if (settingsFile!!.walkingMode) WALKING_THRESHOLD else RUNNING_THRESHOLD
        }
    }

    private fun loadSongs(restartTicking: Boolean = true) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            infoFlow.value = "Loading songs..."
            songsLoadingMutex.lock()
            if (dao != null) songs = dao!!.getEnabledSongsDistinct()
            songsLoadingMutex.unlock()
            if (songs.isEmpty()) infoFlow.value = "There are no songs to be played."
            else if (restartTicking) startTicking()
        }
    }

    private val clock = object : Runnable {
        override fun run() {
            mainHandler.postDelayed(this, 998)
            tick()
        }
    }

    // choosing the song based on the desired bpm
    private fun chooseSongIdx(bpm: Int): Int {
        if (songs.isEmpty()) {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                if (dao?.numOfPlaylists() == 0) infoFlow.value = "You must add some playlist first."
                else infoFlow.value = "There are no songs to be played. You have to enable at least one playlist."
            }
            currentSong = null
            stopTicking()
            return -1
        }
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

        return Random.nextInt(startIndex, stopIndex + 1) //stop is exclusive
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