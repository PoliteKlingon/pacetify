package com.example.pacetify.ui.playlists

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.example.pacetify.MainActivity
import com.example.pacetify.data.PacetifyService
import com.example.pacetify.data.Playlist
import com.example.pacetify.data.source.database.PacetifyDatabase
import com.example.pacetify.databinding.FragmentPlaylistsBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlaylistsFragment : Fragment() {

    private var _binding: FragmentPlaylistsBinding? = null

    private var pacetifyService: PacetifyService? = null
    private var serviceBound: Boolean = false

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as PacetifyService.PacetifyBinder
            pacetifyService = binder.getService()
            serviceBound = true

            Log.d("DashboardFragment", "service connected")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            serviceBound = false
        }
    }

    private fun bindService() {
        Intent(activity!!, PacetifyService::class.java).also { intent ->
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

        val dao = PacetifyDatabase.getInstance(activity!!).pacetifyDao

        playlists = mutableListOf()

        binding.tvNoPlaylists.text = if (playlists.isEmpty()) "No playlists yet" else ""

        class PlaylistAdapterDataObserver: AdapterDataObserver() {
            override fun onChanged() {
                binding.tvNoPlaylists.text = if (playlists.isEmpty()) "No playlists yet" else ""
            }
        }

        val adapter = PlaylistAdapter(playlists, dao, lifecycleScope, activity as MainActivity?,
            serviceBound, pacetifyService, childFragmentManager)
        binding.rvPlaylists.adapter = adapter
        adapter.registerAdapterDataObserver(PlaylistAdapterDataObserver())
        binding.rvPlaylists.layoutManager = LinearLayoutManager(activity)

        lifecycleScope.launch {
            playlists.addAll(dao.getPlaylists())
            adapter.notifyDataSetChanged()
        }

        binding.fabAddPlaylist.setOnClickListener {
            AddPlaylistDialog(
                activity!!, playlists, dao, adapter, serviceBound, pacetifyService, lifecycle
            ).apply {
                setOnDismissListener{
                    insertedPlaylist(adapter)
                    binding.tvNoPlaylists.text = ""
                }
                show()
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