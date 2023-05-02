package com.example.pacetify.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pacetify.MainActivity
import com.example.pacetify.R
import com.example.pacetify.data.source.database.PacetifyDao
import com.example.pacetify.data.source.database.PacetifyDatabase
import com.example.pacetify.databinding.FragmentHomeBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

/**
 * The main fragment of the app - here the user can start the service and then their cadence,
 * currently played song and other running info like resting time left is displayed. The user
 * can also skip a song.
 *
 * author: Jiří Loun
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // These stateFlows will flow from the service and carry all the information.
    // Each has its observer to be able to cancel the flow collecting
    private var cadenceFlow: StateFlow<String>? = null
    private var infoFlow: StateFlow<String>? = null
    private var songNameFlow: StateFlow<String>? = null
    private var songDescriptionFlow: StateFlow<String>? = null

    private var cadenceFlowObserver: Job? = null
    private var InfoFlowObserver: Job? = null
    private var songNameFlowObserver: Job? = null
    private var songDescriptionFlowObserver: Job? = null

    private lateinit var mainActivity: MainActivity
    private var serviceBoundFlowObserver: Job? = null

    private lateinit var powerManager: PowerManager
    private var bgActivityAllowed = true
    private var activityTrackingAllowed = true

    private lateinit var dao: PacetifyDao

    private fun onServiceConnected() {
        binding.btnOnOff.text = getString(R.string.service_on)
        binding.tvCadence.setTextSize(TypedValue.COMPLEX_UNIT_SP, 27.0F)

        if (mainActivity.pacetifyService == null) {
            Log.w("Home Fragment", "The just connected service is null, aborting...")
            return
        }

        // obtain the flows
        val flows = mainActivity.pacetifyService?.getFlows()
        if (flows != null) {
            cadenceFlow = flows[0]
            infoFlow = flows[1]
            songNameFlow = flows[2]
            songDescriptionFlow = flows[3]
        }

        if (cadenceFlow != null && infoFlow != null && songNameFlow != null && songDescriptionFlow != null) {
            binding.tvInfo.text = infoFlow?.value
            binding.tvCadence.text = cadenceFlow?.value
            binding.tvSongName.text = songNameFlow?.value
            binding.tvSongDescription.text = songDescriptionFlow?.value
        }
        binding.btnSkipSong.isEnabled = mainActivity.serviceBoundFlow.value

        observeFlows()
        Log.d("HomeFragment", "service connected")
    }

    private fun observeFlows() {
        if (cadenceFlow == null || infoFlow == null || songNameFlow == null || songDescriptionFlow == null) {
            Log.w("HomeFragment", "Some of the service's flows are null, aborting...")
            return
        }
        
        if (cadenceFlowObserver == null) {
            cadenceFlowObserver = lifecycleScope.launchWhenStarted {
                cadenceFlow?.collectLatest {
                    binding.tvCadence.text = it
                    Log.d("HomeFragment", "cadence updated")
                }
            }
        }

        if (InfoFlowObserver == null) {
            InfoFlowObserver = lifecycleScope.launchWhenStarted {
                infoFlow?.collectLatest {
                    binding.tvInfo.text = it
                    Log.d("HomeFragment", "info updated")
                }
            }
        }

        if (songNameFlowObserver == null) {
            songNameFlowObserver = lifecycleScope.launchWhenStarted {
                songNameFlow?.collectLatest {
                    binding.tvSongName.text = it
                    Log.d("HomeFragment", "songName updated")
                }
            }
        }

        if (songDescriptionFlowObserver == null) {
            songDescriptionFlowObserver = lifecycleScope.launchWhenStarted {
                songDescriptionFlow?.collectLatest {
                    binding.tvSongDescription.text = it
                    Log.d("HomeFragment", "songDescription updated")
                }
            }
        }
    }

    private fun onServiceDisconnected() {
        binding.btnOnOff.text = getString(R.string.service_off)
        binding.tvCadence.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20.0F)

        setDisconnectedUiState()
        binding.tvInfo.text = ""
        binding.tvSongName.text = ""
        binding.tvSongDescription.text = ""
        binding.btnSkipSong.isEnabled = mainActivity.serviceBoundFlow.value

        cadenceFlow = null
        infoFlow = null
        songNameFlow = null
        songDescriptionFlow = null

        cancelFlowObserving()
    }

    private fun enableOnOffButton() {
        binding.btnOnOff.isEnabled = true
        binding.btnOnOff.alpha = 1f
    }

    private fun disableOnOffButton() {
        binding.btnOnOff.isEnabled = false
        binding.btnOnOff.alpha = 0.3f
    }

    private fun setDisconnectedUiState() {
        lifecycleScope.launch {
            try {
                if (!bgActivityAllowed) {
                    binding.tvCadence.text = getString(R.string.activity_background_disabled_warning)
                    disableOnOffButton()
                } else if (!activityTrackingAllowed) {
                    binding.tvCadence.text = getString(R.string.activity_recognition_disabled_warning)
                    disableOnOffButton()
                } else if (dao.numOfPlaylists() == 0) {
                    binding.tvCadence.text = getString(R.string.home_no_playlists)
                    disableOnOffButton()
                } else if (dao.numEnabledSongsDistinct() == 0) {
                    binding.tvCadence.text = getString(R.string.home_no_enabled_playlists)
                    disableOnOffButton()
                } else if (!mainActivity.serviceBoundFlow.value) {
                    binding.tvCadence.text = getString(R.string.service_off_description)
                    enableOnOffButton()
                } else {
                    enableOnOffButton()
                }
            } catch (_: java.lang.NullPointerException) {
                // There is a possibility that during this coroutine the user went away and some
                // of the references became null. In that case, I do not wish to update the text
                // anyways, so I just catch the exception and proceed.
            }
        }
    }

    private fun cancelFlowObserving() {
        cadenceFlowObserver?.cancel(CancellationException())
        cadenceFlowObserver = null
        InfoFlowObserver?.cancel(CancellationException())
        InfoFlowObserver = null
        songNameFlowObserver?.cancel(CancellationException())
        songNameFlowObserver = null
        songDescriptionFlowObserver?.cancel(CancellationException())
        songDescriptionFlowObserver = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onServiceDisconnected()
        serviceBoundFlowObserver?.cancel(CancellationException())
        _binding = null
    }

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mainActivity = requireActivity() as MainActivity
        powerManager = mainActivity.getSystemService(Context.POWER_SERVICE) as PowerManager

        dao = PacetifyDatabase.getInstance(mainActivity).pacetifyDao

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        serviceBoundFlowObserver = lifecycleScope.launchWhenStarted {
            mainActivity.serviceBoundFlow.collectLatest {
                if (it) onServiceConnected() else onServiceDisconnected()
            }
        }

        binding.tvInfo.text = getString(R.string.home_text_default)

        binding.btnSkipSong.isEnabled = mainActivity.serviceBoundFlow.value

        binding.btnSkipSong.setOnClickListener {
            if (mainActivity.serviceBoundFlow.value) {
                mainActivity.pacetifyService?.orderSkipSong()
            } else {
                Toast.makeText(requireActivity(), "Service is not active", Toast.LENGTH_SHORT).show()
            }
        }

        // service manipulation
        binding.btnOnOff.setOnClickListener {
            if (mainActivity.serviceBoundFlow.value) {
                mainActivity.unbindService()
                mainActivity.stopService()
            } else {
                mainActivity.startService()
                mainActivity.bindService()
            }
        }

        if (cadenceFlow == null)
            setDisconnectedUiState()
        if (infoFlow == null) binding.tvInfo.text = ""
        if (songNameFlow == null) binding.tvSongName.text = ""
        if (songDescriptionFlow == null) binding.tvSongDescription.text = ""

        requestActivityPermission()
        requestBackgroundPermission()
        return root
    }

    private fun requestActivityPermission() {
        // Here I need to obtain the permission to read the user's activity
        val requestPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            activityTrackingAllowed = isGranted
            setDisconnectedUiState()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activityTrackingAllowed = ContextCompat.checkSelfPermission(
                                        mainActivity, Manifest.permission.ACTIVITY_RECOGNITION
                                        ) == PackageManager.PERMISSION_GRANTED

            if (!activityTrackingAllowed)
                requestPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            activityTrackingAllowed = true
        }
    }

    private fun requestBackgroundPermission() {
        // The same for another permission
        if (!powerManager.isIgnoringBatteryOptimizations(mainActivity.packageName)) {
            AlertDialog.Builder(mainActivity)
                .setTitle("Please allow background activity for Pacetify")
                .setMessage("Pacetify will not measure your cadence correctly without background activity " +
                        "enabled. \nPlease enable it (Apps -> Pacetify -> Battery usage -> Allow " +
                        "background activity) and restart Pacetify.")
                .setPositiveButton("OK")  { dialog, _ -> dialog.dismiss() }
                .show()

            bgActivityAllowed = false
            setDisconnectedUiState()
        }
        else bgActivityAllowed = true
    }

    override fun onResume() {
        super.onResume()
        if (mainActivity.serviceBoundFlow.value) {
            observeFlows()
        }
    }

    override fun onPause() {
        super.onPause()
        if (mainActivity.serviceBoundFlow.value) {
            cancelFlowObserving()
        }
    }
}