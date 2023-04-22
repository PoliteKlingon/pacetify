package com.example.pacetify.ui.playlists

import android.os.Bundle
import android.util.Log
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

    private fun setPlaylistsWarning() {
        Log.d("ASD", playlists.map { playlist: Playlist -> playlist.name }.toString())
        binding.tvPlaylistsWarning.text =
            if (playlists.isEmpty()) getString(R.string.no_playlists)
            else if (playlists.size < 7) getString(R.string.few_playlists)
            else ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // we need the activity reference so we can communicate with the service through it
        val mainActivity = requireActivity() as MainActivity

        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val dao = PacetifyDatabase.getInstance(mainActivity).pacetifyDao

        playlists = mutableListOf()

        // the tvPlaylistWarning is a textView in which I display the info about no (or few)
        // playlists being present, but I need it to react to changes. Hence the observer.
        setPlaylistsWarning()
        class PlaylistAdapterDataObserver: AdapterDataObserver() {
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                super.onItemRangeRemoved(positionStart, itemCount)
                setPlaylistsWarning()
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                setPlaylistsWarning()
            }

            override fun onChanged() {
                super.onChanged()
                setPlaylistsWarning()
            }
        }

        // the adapter for the playlists recyclerView
        val adapter = PlaylistAdapter(playlists, dao, lifecycleScope, activity as MainActivity,
            childFragmentManager)
        adapter.registerAdapterDataObserver(PlaylistAdapterDataObserver())
        binding.rvPlaylists.adapter = adapter
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
            while((activity as MainActivity?)?.webApi?.isNetworkBeingUsed() == true) {
                delay(500) //TODO is there a better way?
                adapter.notifyDataSetChanged()
            }
            delay(500)
            adapter.notifyDataSetChanged()
        }
    }
}