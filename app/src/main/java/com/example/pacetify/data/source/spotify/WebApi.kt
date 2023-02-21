package com.example.pacetify.data.source.spotify

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.pacetify.MainActivity
import com.example.pacetify.data.Playlist
import com.example.pacetify.data.Song
import com.example.pacetify.data.source.database.PacetifyDatabase
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class WebApi(val activity: MainActivity) {

    companion object {
        @Volatile
        private var INSTANCE: WebApi? = null

        fun getInstance(activity: MainActivity): WebApi {
            synchronized(this) {
                return INSTANCE ?: WebApi(activity).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val CLIENT_ID = "29755c71ec3a4765aec6d780e0b71214"
    private val REDIRECT_URI = "com.example.pacetify://callback" //TODO??
    //private val AUTH_TOKEN_REQUEST_CODE = 0x10

    private val mOkHttpClient = OkHttpClient()
    private var mAccessToken: String? = null
    private var mCall: Call? = null

    private var connectivityManager: ConnectivityManager =
        activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            if (mAccessToken == null) requestToken(activity)
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

    init {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    private var resultLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val response = AuthorizationClient.getResponse(
                    result.resultCode,
                    data
                )

                if (response.error != null && response.error.isNotEmpty()) {
                    Log.d("Main", response.error)
                } else {
                    mAccessToken = response.accessToken
                    Log.d("Main", "Access token: ${mAccessToken.toString()}")
                }
            }
        }

    private var dao = PacetifyDatabase.getInstance(activity).pacetifyDao

    private fun requestToken(activity: MainActivity) {
        val request: AuthorizationRequest =
            AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI)
                .setShowDialog(false)
                .setScopes(arrayOf()) //we do not need any special scopes
                .setCampaign("") //no campaign needed
                .build()

        val authIntent = AuthorizationClient.createLoginActivityIntent(activity, request)
        resultLauncher.launch(authIntent)
    }

    private fun cancelCall() {
        mCall?.cancel()
    }

    fun addSongsFromPlaylist(playlist: Playlist, lifecycleScope: LifecycleCoroutineScope) {
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
                        //As the web API does support getting at most 50 results at a time,
                        // we have to split the query
                        addLimitedSongsFromPlaylist(playlist, currentItems, 50, lifecycleScope)
                        currentItems += 50
                    }
                } catch (e: JSONException) {
                    Log.d("MainActivity","Failed to parse data: $e")
                }
            }
        })
    }

    private fun addLimitedSongsFromPlaylist(playlist: Playlist, offset: Int, limit: Int, lifecycleScope: LifecycleCoroutineScope) {
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
                        addSongWithName(songUri, playlist.name, true, lifecycleScope)
                    }
                } catch (e: JSONException) {
                    Log.d("MainActivity","Failed to parse data: $e")
                }
            }
        })
    }

    fun addSongWithName(songUri: String, playlistName: String, isFromPlaylist: Boolean, lifecycleScope: LifecycleCoroutineScope) {
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
                    val artistName = jsonObject.getJSONArray("artists")
                        .getJSONObject(0).getString("name")

                    addSong(songUri, playlistName, songName, artistName, isFromPlaylist, lifecycleScope)

                } catch (e: JSONException) {
                    Log.d("MainActivity","Failed to parse data: $e")
                    //we do not add songs with unknown bpm
                }
            }
        })
    }

    private fun addSong(songUri: String, playlistName: String, songName: String, artistName: String, isFromPlaylist: Boolean, lifecycleScope: LifecycleCoroutineScope) {
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

    fun isTokenAcquired(): Boolean {
        return mAccessToken != null
    }

    fun isNetworkBeingUsed(): Boolean {
        return mCall != null //TODO - this may cause the random playlist issues - a single mCall does not sound ok
    }
}
