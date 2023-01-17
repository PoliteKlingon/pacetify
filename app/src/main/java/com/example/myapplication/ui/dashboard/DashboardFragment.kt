package com.example.myapplication.ui.dashboard

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
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
import com.example.myapplication.SRADatabase
import com.example.myapplication.SRAService
import com.example.myapplication.databinding.FragmentDashboardBinding
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null

    private var sraService: SRAService? = null
    private var serviceBound: Boolean = false

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as SRAService.SRABinder
            sraService = binder.getService()
            serviceBound = true

            Log.d("DashboardFragment", "service connected")
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
        binding.btnAddPlaylist.isEnabled = false
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
                Toast.makeText(activity, "Playlist \"$name\" already exists", Toast.LENGTH_LONG).show()
            else if (sraService == null) {
                Toast.makeText(activity, "For adding playlists, the service must be running", Toast.LENGTH_LONG).show()
            }
            else if (!sraService!!.isValidUri(uri))
                Toast.makeText(activity, "Invalid playlist URL", Toast.LENGTH_LONG).show()
            else {
                val id = uri.takeLastWhile { ch -> ch != '/' }
                val playlist = Playlist("spotify:playlist:$id", name) //TODO asi spatne tvorene uri
                val songs = sraService?.getSongsFromPlaylist(playlist)
                if (songs == null) {
                    Toast.makeText(activity, "Invalid playlist URL", Toast.LENGTH_LONG).show()
                } else {
                    playlists.add(playlist)
                    binding.tvNoPlaylists.text = ""
                    adapter.notifyItemInserted(playlists.size - 1)
                    binding.etNewPlaylistName.setText("")
                    binding.etNewPlaylistUri.setText("")
                    lifecycleScope.launch {
                        if (sraService != null) {
                            for (song in songs) {
                                dao.insertSong(song)
                            }
                            dao.insertPlaylist(playlist)
                            sraService!!.notifyPlaylistsChanged()
                        }
                    }
                }
            }
        }

        return root
    }
}