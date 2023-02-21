package com.example.pacetify

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.pacetify.data.PacetifyService
import com.example.pacetify.data.Playlist
import com.example.pacetify.data.Song
import com.example.pacetify.data.source.database.PacetifyDao
import com.example.pacetify.data.source.database.PacetifyDatabase
import com.example.pacetify.databinding.ActivityMainBinding
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val CLIENT_ID = "29755c71ec3a4765aec6d780e0b71214"
    private val REDIRECT_URI = "com.example.pacetify://callback" //TODO??
    private val AUTH_TOKEN_REQUEST_CODE = 0x10

    private val mOkHttpClient = OkHttpClient()
    private var mAccessToken: String? = null
    private var mCall: Call? = null

    private var dao: PacetifyDao? = null

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: NetworkCallback

    var pacetifyService: PacetifyService? = null
    var serviceBound: Boolean = false

    lateinit var serviceBoundFlow: MutableStateFlow<Boolean>

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as PacetifyService.PacetifyBinder
            pacetifyService = binder.getService()
            serviceBound = true
            serviceBoundFlow.value = true

            Log.d("MainActivity", "service connected")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            serviceBound = false
            serviceBoundFlow.value = false
        }
    }

    fun bindService() {
        Intent(this, PacetifyService::class.java).also { intent ->
            bindService(intent, connection, 0)
        }
    }

    fun unbindService() {
        unbindService(connection)
        serviceBound = false
        serviceBoundFlow.value = false
    }

    private fun cancelCall() {
        mCall?.cancel()
    }

    fun addSongsFromPlaylist(playlist: Playlist) {
        val request: Request = Request.Builder()
            .url("https://api.spotify.com/v1/playlists/${playlist.id}/tracks")
            .addHeader("Authorization", "Bearer $mAccessToken")
            .build()

        mCall = mOkHttpClient.newCall(request)

        mCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("MainActivity","Failed to fetch data: $e")
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                try {
                    val jsonObject = JSONObject(response.body!!.string())
                    val totalItems = jsonObject.getString("total").toInt()
                    var currentItems = 0
                    while (currentItems < totalItems) {
                        //As the web API does support getting at most 50 results at a time, we have to split the query
                        addLimitedSongsFromPlaylist(playlist, currentItems, 50)
                        currentItems += 50
                    }
                } catch (e: JSONException) {
                    Log.d("MainActivity","Failed to parse data: $e")
                }
            }
        })
    }

    private fun addLimitedSongsFromPlaylist(playlist: Playlist, offset: Int, limit: Int) {
        val request: Request = Request.Builder()
            .url("https://api.spotify.com/v1/playlists/${playlist.id}/tracks?offset=$offset&limit=$limit")
            .addHeader("Authorization", "Bearer $mAccessToken")
            .build()

        mCall = mOkHttpClient.newCall(request)

        mCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("MainActivity","Failed to fetch data: $e")
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                try {
                    val jsonObject = JSONObject(response.body!!.string())
                    val arr = jsonObject.getJSONArray("items")
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val songUri = item.getJSONObject("track").getString("uri")
                        addSongWithName(songUri, playlist.name, true)
                    }
                } catch (e: JSONException) {
                    Log.d("MainActivity","Failed to parse data: $e")
                }
            }
        })
    }

    fun addSongWithName(songUri: String, playlistName: String, isFromPlaylist: Boolean) {
        val songId = songUri.takeLastWhile { ch -> ch != ':' }
        val request: Request = Request.Builder()
            .url("https://api.spotify.com/v1/tracks/$songId")
            .addHeader("Authorization", "Bearer $mAccessToken")
            .build()

        mCall = mOkHttpClient.newCall(request)

        mCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("MainActivity","Failed to fetch data: $e")
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                try {
                    val jsonObject = JSONObject(response.body!!.string())
                    val songName = jsonObject.getString("name")
                    val artistName = jsonObject.getJSONArray("artists").getJSONObject(0).getString("name")

                    addSong(songUri, playlistName, songName, artistName, isFromPlaylist)

                } catch (e: JSONException) {
                    Log.d("MainActivity","Failed to parse data: $e") //we do not add songs with unknown bpm
                }
            }
        })
    }

    private fun addSong(songUri: String, playlistName: String, songName: String, artistName: String, isFromPlaylist: Boolean) {
        val songId = songUri.takeLastWhile { ch -> ch != ':' }
        val request: Request = Request.Builder()
            .url("https://api.spotify.com/v1/audio-features/$songId")
            .addHeader("Authorization", "Bearer $mAccessToken")
            .build()

        mCall = mOkHttpClient.newCall(request)

        mCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("MainActivity","Failed to fetch data: $e")
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                try {
                    val jsonObject = JSONObject(response.body!!.string())
                    val bpm = jsonObject.getString("tempo").takeWhile { ch -> ch != '.' }.toInt()
                    lifecycleScope.launch {
                        val song = Song(songUri, songName, artistName, bpm, playlistName)
                        if (isFromPlaylist) dao?.safeInsertSong(song)
                        else                dao?.insertSong(song)
                        Log.d("MainActivity", "inserted song: $song")
                    }
                } catch (e: JSONException) {
                    Log.d("MainActivity","Failed to parse data: $e") //we do not add songs with unknown bpm
                }
            }
        })
    }

    private fun requestToken() {
        val request: AuthorizationRequest =
            AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI)
                .setShowDialog(false)
                .setScopes(arrayOf()) //we do not need any special scopes
                .setCampaign("") //no campaign needed
                .build()
        AuthorizationClient.openLoginActivity(
            this,
            AUTH_TOKEN_REQUEST_CODE,
            request
        )
    }

    fun isTokenAcquired(): Boolean {
        return mAccessToken != null
    }

    fun isNetworkBeingUsed(): Boolean {
        return mCall != null //TODO - this may cause the random playlist issues - a single mCall does not sound ok
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val response = AuthorizationClient.getResponse(resultCode, data)
        if (response.error != null && response.error.isNotEmpty()) {
            Log.d("Main", response.error)
        }
        if (requestCode == AUTH_TOKEN_REQUEST_CODE) {
            mAccessToken = response.accessToken
            Log.d("Main", mAccessToken.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        bindService()
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        if (dao == null) dao = PacetifyDatabase.getInstance(this).pacetifyDao
    }

    override fun onPause() {
        super.onPause()
        if (serviceBound) unbindService()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) unbindService()
        cancelCall()
    }

    fun startService(tick: Boolean = true) {
        Intent(this, PacetifyService::class.java).also {
            it.putExtra("tick", tick)
            this.startService(it)
        }
    }

    fun stopService() {
        Intent(this, PacetifyService::class.java).also {
            this.stopService(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        serviceBoundFlow = MutableStateFlow(false)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object: NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                if (mAccessToken == null) requestToken()
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                mAccessToken = null
            }

            override fun onUnavailable() {
                super.onUnavailable()
                mAccessToken = null
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_playlists, R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }
}