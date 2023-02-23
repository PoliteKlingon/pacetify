package com.example.pacetify.ui.playlists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.example.pacetify.MainActivity
import com.example.pacetify.R
import com.example.pacetify.data.Playlist
import com.example.pacetify.data.source.database.PacetifyDatabase
import com.example.pacetify.databinding.FragmentPlaylistsBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * In this fragment the user manages (display, add, delete) their imported playlists and songs
 * that are then played while running.
 */
class PlaylistsFragment : Fragment() {

    private var _binding: FragmentPlaylistsBinding? = null

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
        /*val playlistsViewModel =
            ViewModelProvider(this).get(PlaylistsViewModel::class.java)*/

        // we need the activity reference so we can communicate with the service through it
        val mainActivity = requireActivity() as MainActivity

        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        /*val textView: TextView = binding.textDashboard
        dashboardViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }*/

        val dao = PacetifyDatabase.getInstance(mainActivity).pacetifyDao

        playlists = mutableListOf()

        // this is a textView in which we display the info about no playlists being present, but
        // as soon as at least a single playlist is present, we leave it blank. Hence the observer.
        binding.tvNoPlaylists.text = if (playlists.isEmpty()) getString(R.string.no_playlists) else ""

        class PlaylistAdapterDataObserver: AdapterDataObserver() {
            override fun onChanged() {
                binding.tvNoPlaylists.text = if (playlists.isEmpty()) getString(R.string.no_playlists) else ""
            }
        }

        // the adapter for the playlists recyclerView
        val adapter = PlaylistAdapter(playlists, dao, lifecycleScope, activity as MainActivity,
            childFragmentManager)
        binding.rvPlaylists.adapter = adapter
        adapter.registerAdapterDataObserver(PlaylistAdapterDataObserver())
        binding.rvPlaylists.layoutManager = LinearLayoutManager(activity)

        lifecycleScope.launch {
            playlists.addAll(dao.getPlaylists())
            adapter.notifyDataSetChanged()
        }

        // the option to add a playlist
        binding.fabAddPlaylist.setOnClickListener {
            AddPlaylistDialog(
                mainActivity, playlists, dao, adapter, lifecycle
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

    // this function ensures the recyclerView displayed song numbers to be updated as long as the actual song
    // count is updated
    private fun insertedPlaylist(adapter: PlaylistAdapter) {
        lifecycleScope.launch {
            while((activity as MainActivity?)?.isNetworkBeingUsed() == true) {
                delay(500) //TODO is there a better way?
                adapter.notifyDataSetChanged()
            }
        }
    }
}