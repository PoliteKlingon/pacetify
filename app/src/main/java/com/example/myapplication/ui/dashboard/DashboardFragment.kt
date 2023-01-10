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
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.example.myapplication.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var playlists: MutableList<Playlist>
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

        playlists = mutableListOf() //TODO: nacitani ze souboru
        binding.tvNoPlaylists.setText(if (playlists.isEmpty()) "No playlists yet" else "") //TODO: vratit tam text kdyz vsechny vymaze

        class PlaylistAdapterDataObserver: AdapterDataObserver() {
            override fun onChanged() {
                binding.tvNoPlaylists.setText(if (playlists.isEmpty()) "No playlists yet" else "")
            }
        }

        val adapter = PlaylistAdapter(playlists)
        binding.rvPlaylists.adapter = adapter
        adapter.registerAdapterDataObserver(PlaylistAdapterDataObserver())
        binding.rvPlaylists.layoutManager = LinearLayoutManager(activity)

        binding.btnAddPlaylist.setOnClickListener {
            val name = binding.etNewPlaylistName.text.toString()
            val uri = binding.etNewPlaylistUri.text.toString()
            if (name.isEmpty() || uri.isEmpty()) { //TODO: more checks?
                Toast.makeText(activity, "Name and URL can not be empty", Toast.LENGTH_LONG).show()
            } else {
                playlists.add(Playlist(uri, name))
                binding.tvNoPlaylists.setText("")
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