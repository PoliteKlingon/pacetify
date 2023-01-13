package com.example.myapplication.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.example.myapplication.Playlist
import com.example.myapplication.SRADao
import com.example.myapplication.SRADatabase
import com.example.myapplication.databinding.FragmentDashboardBinding
import kotlinx.coroutines.launch

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

        val dao = SRADatabase.getInstance(activity!!).SRADao

        playlists = mutableListOf()

        binding.tvNoPlaylists.text = if (playlists.isEmpty()) "No playlists yet" else ""

        class PlaylistAdapterDataObserver: AdapterDataObserver() {
            override fun onChanged() {
                binding.tvNoPlaylists.text = if (playlists.isEmpty()) "No playlists yet" else ""
            }
        }

        val adapter = PlaylistAdapter(playlists, dao, lifecycleScope)
        binding.rvPlaylists.adapter = adapter
        adapter.registerAdapterDataObserver(PlaylistAdapterDataObserver())
        binding.rvPlaylists.layoutManager = LinearLayoutManager(activity)

        lifecycleScope.launch {
            playlists.addAll(dao.getPlaylists())
            adapter.notifyDataSetChanged()
        }

        binding.btnAddPlaylist.setOnClickListener {
            val name = binding.etNewPlaylistName.text.toString()
            val uri = binding.etNewPlaylistUri.text.toString()

            if (name.isEmpty())
                Toast.makeText(activity, "Name can not be empty", Toast.LENGTH_LONG).show()
            else if (uri.isEmpty())
                Toast.makeText(activity, "URL can not be empty", Toast.LENGTH_LONG).show()
            else if (playlists.map { p -> p.name } .contains(name))
                Toast.makeText(activity, "Playlist \"$name\" already exists", Toast.LENGTH_LONG).show() //TODO pridat check validity URL
            else {
                val playlist = Playlist(uri, name)
                playlists.add(playlist)
                binding.tvNoPlaylists.text = ""
                adapter.notifyItemInserted(playlists.size - 1)
                binding.etNewPlaylistName.setText("")
                binding.etNewPlaylistUri.setText("")
                lifecycleScope.launch {
                    dao.addPlaylist(playlist)
                }
            }
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}