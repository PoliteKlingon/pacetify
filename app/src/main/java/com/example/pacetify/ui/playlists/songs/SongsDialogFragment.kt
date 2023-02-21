package com.example.pacetify.ui.playlists.songs

import android.content.DialogInterface
import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pacetify.MainActivity
import com.example.pacetify.data.PacetifyService
import com.example.pacetify.data.Song
import com.example.pacetify.data.source.database.PacetifyDatabase
import com.example.pacetify.databinding.DialogFragmentSongsBinding
import com.example.pacetify.ui.playlists.PlaylistAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SongsDialogFragment(
    val serviceBound: Boolean,
    val sraService: PacetifyService?,
    val adapter: PlaylistAdapter,
    val playlistName: String
): DialogFragment() {

    private var _binding: DialogFragmentSongsBinding? = null

    private val binding get() = _binding!!
    private lateinit var songs: MutableList<Song>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        isCancelable = true

        _binding = DialogFragmentSongsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val dao = PacetifyDatabase.getInstance(requireActivity()).pacetifyDao

        songs = mutableListOf()

        binding.tvNoSongs.text = if (songs.isEmpty()) "No playlists yet" else ""

        class PlaylistAdapterDataObserver: RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                binding.tvNoSongs.text = if (songs.isEmpty()) "No playlists yet" else ""
            }
        }

        val adapter = SongAdapter(songs, dao, lifecycleScope, activity as MainActivity?)
        binding.rvSongs.adapter = adapter
        adapter.registerAdapterDataObserver(PlaylistAdapterDataObserver())
        binding.rvSongs.layoutManager = LinearLayoutManager(activity)

        lifecycleScope.launch {
            songs.addAll(dao.getSongsFromPlaylist(playlistName))
            adapter.notifyDataSetChanged()
        }

        binding.fabAddSong.setOnClickListener {
            AddSongDialog(
                requireActivity(), songs, dao, lifecycle, playlistName
            ).apply {
                setOnDismissListener{
                    songs.removeAll { true }
                    lifecycleScope.launch {
                        delay(1000) // Wait for the song to load
                        songs.addAll(dao.getSongsFromPlaylist(playlistName))
                        adapter.notifyDataSetChanged()
                        binding.tvNoSongs.text = ""
                        if (serviceBound) sraService?.notifyPlaylistsChanged()
                    }
                }
                show()
            }
        }

        //TODO to enable playing the songs on tap:
        //TODO if servicebound, unbind and stpo the service
        //TODO connect to remote
        //TODO add id to the song xml, add onclicklistener with mremote.play to the id

        return root
    }

    override fun onResume() {
        super.onResume()
        val windowAttributes = dialog?.window?.attributes?.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
        dialog?.window?.attributes = windowAttributes
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        adapter.notifyDataSetChanged()
    }
}