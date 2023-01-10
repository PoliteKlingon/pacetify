package com.example.myapplication.ui.dashboard

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel =
            ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        /*val textView: TextView = binding.textDashboard
        dashboardViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }*/

        var playlists = mutableListOf<Playlist>() //TODO: nacitani ze souboru
        val adapter = PlaylistAdapter(playlists)
        binding.rvPlaylists.adapter = adapter
        binding.rvPlaylists.layoutManager = LinearLayoutManager(activity)

        binding.btnAddPlaylist.setOnClickListener {
            val name = binding.etNewPlaylistName.text.toString()
            val uri = binding.etNewPlaylistUri.text.toString()
            if (name.isEmpty() || uri.isEmpty()) { //TODO: more checks?
                Toast.makeText(activity, "Name and URL can not be empty", Toast.LENGTH_LONG).show()
            } else {
                playlists.add(Playlist(uri, name))
                adapter.notifyItemInserted(playlists.size - 1)
                binding.etNewPlaylistName.setText("")
                binding.etNewPlaylistUri.setText("") //TODO: ulozeni do souboru
            }
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}