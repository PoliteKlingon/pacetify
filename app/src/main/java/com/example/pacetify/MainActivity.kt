package com.example.pacetify

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.pacetify.data.PacetifyService
import com.example.pacetify.data.Playlist
import com.example.pacetify.data.source.spotify.WebApi
import com.example.pacetify.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    var pacetifyService: PacetifyService? = null
    var serviceBound: Boolean = false

    lateinit var serviceBoundFlow: MutableStateFlow<Boolean>


    private lateinit var webApi: WebApi

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

    fun addSongsFromPlaylist(playlist: Playlist) {
        webApi.addSongsFromPlaylist(playlist, lifecycleScope)
    }

    fun addSongWithName(songId: String, playlistName: String) {
        webApi.addSongWithName(songId, playlistName, lifecycleScope)
    }

    fun isTokenAcquired(): Boolean {
        return webApi.isTokenAcquired()
    }

    fun isNetworkBeingUsed(): Boolean {
        return webApi.isNetworkBeingUsed()
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

        webApi = WebApi.getInstance(this)

        serviceBoundFlow = MutableStateFlow(false)

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

    override fun onResume() {
        super.onResume()
        bindService()
    }

    override fun onPause() {
        super.onPause()
        if (serviceBound) unbindService()
    }

    override fun onDestroy() {
        super.onDestroy()
        webApi.onDestroy()
        if (serviceBound) unbindService()
    }
}
