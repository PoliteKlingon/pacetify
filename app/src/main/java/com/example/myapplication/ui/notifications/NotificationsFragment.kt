package com.example.myapplication.ui.notifications

import android.content.Context
import android.hardware.SensorEventListener
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.databinding.FragmentNotificationsBinding

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val notificationsViewModel =
            ViewModelProvider(this).get(NotificationsViewModel::class.java)

        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
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
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}

            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        binding.swMotivate.setOnClickListener {
            prefEditor.putBoolean("motivate", binding.swMotivate.isChecked)
            prefEditor.apply()
        }

        binding.swRest.setOnClickListener {
            prefEditor.putBoolean("rest", binding.swRest.isChecked)
            prefEditor.apply()
            binding.sbRest.isEnabled = binding.swRest.isChecked
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}