package com.example.myapplication.ui.playlists

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
import com.example.myapplication.*
import com.example.myapplication.databinding.FragmentPlaylistsBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlaylistsFragment : Fragment() {

    private var _binding: FragmentPlaylistsBinding? = null

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
        val playlistsViewModel =
            ViewModelProvider(this).get(PlaylistsViewModel::class.java)

        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
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

        val adapter = PlaylistAdapter(playlists, dao, lifecycleScope, activity as MainActivity?)
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
            else if (!Utils.isValidSpotifyPlaylistUri(uri))
                Toast.makeText(activity, "Invalid playlist URL", Toast.LENGTH_LONG).show()
            else if (((activity as MainActivity?)?.isTokenAcquired()) != true) //is null or false
                Toast.makeText(activity, "Please connect to the internet to add a playlist", Toast.LENGTH_LONG).show()
            else {
                var id = uri.takeLastWhile { ch -> ch != '/' }
                if (id.contains('?')) {
                    id = id.takeWhile { ch -> ch != '?' }
                }

                val playlist = Playlist(id, name)

                (activity as MainActivity?)?.addSongsFromPlaylist(playlist)

                playlists.add(playlist)
                binding.tvNoPlaylists.text = ""
                adapter.notifyItemInserted(playlists.size - 1)
                binding.etNewPlaylistName.setText("")
                binding.etNewPlaylistUri.setText("")

                lifecycleScope.launch {
                    dao.insertPlaylist(playlist)
                    if (serviceBound) sraService?.notifyPlaylistsChanged()
                }

                insertedPlaylist(adapter)
            }
        }

        return root
    }

    private fun insertedPlaylist(adapter: PlaylistAdapter) {
        lifecycleScope.launch {
            while((activity as MainActivity?)?.isNetworkBeingUsed() == true) {
                adapter.notifyDataSetChanged()
                delay(1000) //TODO is there a better way?
            }
        }
    }
}