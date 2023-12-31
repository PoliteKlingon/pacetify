package com.xloun.pacetify.data.source.spotify

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.LifecycleCoroutineScope
import com.xloun.pacetify.MainActivity
import com.xloun.pacetify.data.Playlist
import com.xloun.pacetify.data.Song
import com.xloun.pacetify.data.source.database.PacetifyDatabase
import com.xloun.pacetify.util.NotConnectedException
import com.xloun.pacetify.util.UriUtils
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * A singleton class for interacting with the spotify web API.
 * It can connect to the API, authorize and then import playlists/albums and individual songs.
 *
 * author: Jiří Loun
 */

class WebApi(val activity: MainActivity) {

    companion object {
        @Volatile
        private var INSTANCE: WebApi? = null

        // make this class a singleton
        fun getInstance(activity: MainActivity): WebApi {
            synchronized(this) {
                return INSTANCE ?: WebApi(activity).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val CLIENT_ID = "29755c71ec3a4765aec6d780e0b71214"
    private val REDIRECT_URI = "com.xloun.pacetify://callback"

    private val mOkHttpClient = OkHttpClient()
    private var mAccessToken: String? = null
    private var mCall: Call? = null

    // counter for all network requests for the app to know when all requests have been completed
    private var ongoingRequestsCount = 0

    // As soon as the user connects to the internet, I want to connect to the API, but not sooner.
    // I also wish to disconnect when the internet becomes unavailable.
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
        // register the just created callback
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    // create a launcher for the Spotify Auth activity
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

    // request token for the web API
    fun requestToken(activity: MainActivity) {
        val request: AuthorizationRequest =
            AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI)
                .setShowDialog(false)
                .setScopes(arrayOf("app-remote-control")) //I will need to control the Spotify app
                .setCampaign("") //no campaign needed
                .build()

        val authIntent = AuthorizationClient.createLoginActivityIntent(activity, request)
        resultLauncher.launch(authIntent)
    }

    fun onDestroy() {
        mCall?.cancel()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private fun isTokenAcquired(): Boolean {
        return mAccessToken != null
    }

    fun isNetworkBeingUsed(): Boolean {
        return ongoingRequestsCount > 0
    }

    // importing songs from the just created playlist/album
    // this function only gets the total count of tracks in the playlist/album
    fun addSongsFromPlaylist(playlist: Playlist, lifecycleScope: LifecycleCoroutineScope,
                             isAlbum: Boolean) {
        if (!isTokenAcquired()) {
            requestToken(activity)
            throw NotConnectedException()
        }

        val endpoint = if (isAlbum) "albums" else "playlists"
        val request: Request = Request.Builder()
            .url("https://api.spotify.com/v1/$endpoint/${playlist.id}/tracks")
            .addHeader("Authorization", "Bearer $mAccessToken")
            .build()

        mCall = mOkHttpClient.newCall(request)
        ongoingRequestsCount++

        mCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("WebAPI","Failed to fetch data: $e")
                ongoingRequestsCount--
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                try {
                    val jsonObject = JSONObject(response.body!!.string())
                    val totalItems = jsonObject.getString("total").toInt()
                    var currentItems = 0
                    while (currentItems < totalItems) {
                        // As the web API does support getting at most 50 results at a time,
                        // I have to split the query
                        addLimitedSongsFromPlaylist(playlist, currentItems, 50, lifecycleScope,
                            isAlbum)
                        currentItems += 50
                    }
                } catch (e: JSONException) {
                    Log.d("WebAPI","Failed to parse data: $e")
                }
                ongoingRequestsCount--
            }
        })
    }

    // this function gets the songUris from the playlist/album - 50 at a time
    private fun addLimitedSongsFromPlaylist(playlist: Playlist,
                                            offset: Int, limit: Int,
                                            lifecycleScope: LifecycleCoroutineScope,
                                            isAlbum: Boolean) {
        val endpoint = if (isAlbum) "albums" else "playlists"
        val request: Request = Request.Builder()
            .url("https://api.spotify.com/v1/$endpoint/${playlist.id}/tracks?offset=$offset&limit=$limit")
            .addHeader("Authorization", "Bearer $mAccessToken")
            .build()

        mCall = mOkHttpClient.newCall(request)
        ongoingRequestsCount++

        mCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("WebAPI","Failed to fetch data: $e")
                ongoingRequestsCount--
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                try {
                    val jsonObject = JSONObject(response.body!!.string())
                    val arr = jsonObject.getJSONArray("items")
                    val songUris = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val songUri =
                            if (isAlbum) item.getString("uri")
                            else item.getJSONObject("track").getString("uri")

                        songUris.add(songUri)
                    }
                    addMultipleSongsWithName(songUris, playlist.name, lifecycleScope)
                } catch (e: JSONException) {
                    Log.d("WebAPI","Failed to parse data: $e")
                }
                ongoingRequestsCount--
            }
        })
    }

    // this function creates Songs with basic info from the uris - 50 at a time
    private fun addMultipleSongsWithName(songUris: List<String>, playlistName: String,
                                         lifecycleScope: LifecycleCoroutineScope) {
        val songIds = songUris.map { songUri -> UriUtils.extractIdFromUri(songUri) }
        val request: Request = Request.Builder()
            .url("https://api.spotify.com/v1/tracks?ids=${songIds.joinToString(separator = ",")}")
            .addHeader("Authorization", "Bearer $mAccessToken")
            .build()

        mCall = mOkHttpClient.newCall(request)
        ongoingRequestsCount++

        mCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("WebAPI","Failed to fetch data: $e")
                ongoingRequestsCount--
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                try {
                    val jsonObject = JSONObject(response.body!!.string())

                    val jsonSongs = jsonObject.getJSONArray("tracks")

                    val songs = mutableListOf<Song>()
                    for (i in 0 until jsonSongs.length()) {
                        val item = jsonSongs.getJSONObject(i)
                        val songUri = item.getString("uri")
                        val songName = item.getString("name")
                        val artistName = item.getJSONArray("artists")
                            .getJSONObject(0).getString("name")

                        songs.add(Song(songUri, songName, artistName, -1, playlistName))
                    }

                    addMultipleSongs(songs, lifecycleScope)

                } catch (e: JSONException) {
                    Log.d("WebAPI","Failed to parse data: $e")
                }
                ongoingRequestsCount--
            }
        })
    }

    // this function adds the bpm to multiple songs - 50 at a time
    private fun addMultipleSongs(songs: List<Song>, lifecycleScope: LifecycleCoroutineScope) {
        val songIds = songs.map { song -> UriUtils.extractIdFromUri(song.uri) }
        val request: Request = Request.Builder()
            .url("https://api.spotify.com/v1/audio-features?ids=${songIds.joinToString(separator = ",")}")
            .addHeader("Authorization", "Bearer $mAccessToken")
            .build()

        mCall = mOkHttpClient.newCall(request)
        ongoingRequestsCount++

        mCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("WebAPI","Failed to fetch data: $e")
                ongoingRequestsCount--
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                try {
                    val jsonObject = JSONObject(response.body!!.string())
                    val jsonSongs = jsonObject.getJSONArray("audio_features")

                    for (i in 0 until jsonSongs.length()) {
                        val item = jsonSongs.getJSONObject(i)
                        val bpm = item.getString("tempo").takeWhile { ch -> ch != '.' }.toInt()
                        val songUri = item.getString("uri")

                        if (songUri == songs[i].uri) {
                            val song = songs[i].copy(bpm = bpm)
                            lifecycleScope.launch {
                                dao.insertSong(song)
                                Log.d("WebAPI", "inserted song: $song")
                            }
                        } else {
                            Log.d("WebAPI", "request and response song uri do not match")
                        }
                    }
                } catch (e: JSONException) {
                    Log.d("WebAPI","Failed to parse data: $e")
                    //I do not add songs with unknown bpm
                }
                ongoingRequestsCount--
            }
        })
    }

    // import a single song
    // get the basic info about the song
    fun addSongWithName(songUri: String, playlistName: String, lifecycleScope: LifecycleCoroutineScope) {
        if (!isTokenAcquired()) {
            requestToken(activity)
            throw NotConnectedException()
        }

        addMultipleSongsWithName(listOf(songUri), playlistName, lifecycleScope)
    }
}
