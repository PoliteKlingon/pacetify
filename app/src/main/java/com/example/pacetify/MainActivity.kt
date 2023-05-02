package com.example.pacetify

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.pacetify.data.source.preferenceFiles.SettingsPreferenceFile
import com.example.pacetify.data.source.spotify.WebApi
import com.example.pacetify.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The main (and the only one) activity in Pacetify
 *
 * author: Jiří Loun
 */

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    var pacetifyService: PacetifyService? = null
    // This is a flow in which I will store whether the service is bound
    lateinit var serviceBoundFlow: MutableStateFlow<Boolean>

    lateinit var webApi: WebApi

    // service connection object to deal with the (dis)connected service
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as PacetifyService.PacetifyBinder
            pacetifyService = binder.getService()
            serviceBoundFlow.value = true

            Log.d("MainActivity", "service connected")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            serviceBoundFlow.value = false
            pacetifyService = null
        }
    }

    // bind to the already running service
    fun bindService() {
        Intent(this, PacetifyService::class.java).also { intent ->
            bindService(intent, connection, 0)
        }
    }

    // unbind from the running service
    fun unbindService() {
        unbindService(connection)
        serviceBoundFlow.value = false
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

    fun notifyServiceSettings() {
        if (serviceBoundFlow.value)
            pacetifyService?.notifySettingsChanged()
    }

    fun notifyServicePlaylists(restartTicking: Boolean = true) {
        if (serviceBoundFlow.value)
            pacetifyService?.notifyPlaylistsChanged(restartTicking = restartTicking)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(SettingsPreferenceFile.getInstance(this).themeResource)
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
        if (serviceBoundFlow.value) unbindService()
    }

    override fun onDestroy() {
        super.onDestroy()
        webApi.onDestroy()
        if (serviceBoundFlow.value) unbindService()
    }
}
