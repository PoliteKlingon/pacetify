package com.example.myapplication.ui.notifications

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

        binding.sbRest.progress = 1 //TODO: nacist nastaveni
        binding.sbRest.isEnabled = true //TODO: nacist nastaveni
        binding.sbRest.isEnabled = binding.swRest.isChecked
        binding.tvRest.setText("Maximal resting time: " + ((binding.sbRest.progress + 1) * 10) + " s")

        binding.sbRest.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekbar: SeekBar?, progress: Int, fromUser: Boolean) {
                val curRestTime = (progress + 1) * 10
                binding.tvRest.setText("Maximal resting time: " + curRestTime + " s")
                //TODO: ulozit nastaveni rest time
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}

            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        binding.swMotivate.isChecked = true
        //TODO: nacist nastaveni

        binding.swMotivate.setOnClickListener {
            //TODO: ulozit nastaveni
        }

        binding.swRest.isChecked = false //TODO: nacist nastaveni

        binding.swRest.setOnClickListener {
            //TODO: ulozit nastaveni
            binding.sbRest.isEnabled = binding.swRest.isChecked
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}