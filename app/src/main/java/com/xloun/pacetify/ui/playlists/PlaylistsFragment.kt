package com.xloun.pacetify.ui.playlists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.xloun.pacetify.MainActivity
import com.xloun.pacetify.R
import com.xloun.pacetify.data.Playlist
import com.xloun.pacetify.data.source.database.PacetifyDatabase
import com.xloun.pacetify.databinding.FragmentPlaylistsBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * In this fragment the user manages (display, add, delete) their imported playlists and songs
 * that are then played while running.
 *
 * author: Jiří Loun
 */
class PlaylistsFragment : Fragment() {

    private var _binding: FragmentPlaylistsBinding? = null

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private lateinit var playlists: MutableList<Playlist>

    private fun setPlaylistsWarning() {
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
        // I need the activity reference so I can communicate with the service through it
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
            override fun onChanged() { // generic change
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
                delay(500)
                adapter.notifyDataSetChanged()
            }
            delay(500) // in case of any late database update
            adapter.notifyDataSetChanged()
        }
    }
}