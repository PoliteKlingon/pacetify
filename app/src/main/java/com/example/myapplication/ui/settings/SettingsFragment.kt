package com.example.myapplication.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.SRAService
import com.example.myapplication.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var sraService: SRAService? = null
    private var serviceBound: Boolean = false

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as SRAService.SRABinder
            sraService = binder.getService()
            serviceBound = true

            Log.d("NotificationsFragment", "service connected")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            serviceBound = false
        }
    }

    private fun bindService() {
        Intent(activity!!, SRAService::class.java).also { intent ->
            activity!!.bindService(intent, connection, 0)
        }
    }

    private fun unbindService() {
        activity!!.unbindService(connection)
        serviceBound = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (serviceBound) unbindService()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        bindService()
    }

    override fun onPause() {
        super.onPause()
        if (serviceBound) unbindService()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val settingsViewModel =
            ViewModelProvider(this).get(SettingsViewModel::class.java)

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        /*val textView: TextView = binding.textNotifications
        notificationsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }*/

        val sharedPref = activity!!.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val prefEditor = sharedPref.edit()

        binding.swMotivate.isChecked = sharedPref.getBoolean("motivate", true)
        binding.swRest.isChecked = sharedPref.getBoolean("rest", true)
        binding.sbRest.progress = sharedPref.getInt("progress", 1)
        binding.sbRest.isEnabled = binding.swRest.isChecked
        binding.tvRest.text = "Maximal resting time: " + ((binding.sbRest.progress + 1) * 10) + " s"

        binding.sbRest.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekbar: SeekBar?, progress: Int, fromUser: Boolean) {
                val curRestTime = (progress + 1) * 10
                binding.tvRest.text = "Maximal resting time: $curRestTime s"

                prefEditor.putInt("progress", progress)
                prefEditor.apply()
                if (serviceBound) sraService?.notifySettingsChanged()
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}

            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        binding.swMotivate.setOnClickListener {
            prefEditor.putBoolean("motivate", binding.swMotivate.isChecked)
            prefEditor.apply()
            if (serviceBound) sraService?.notifySettingsChanged()
        }

        binding.swRest.setOnClickListener {
            prefEditor.putBoolean("rest", binding.swRest.isChecked)
            prefEditor.apply()
            binding.sbRest.isEnabled = binding.swRest.isChecked
            if (serviceBound) sraService?.notifySettingsChanged()
        }

        return root
    }
}