package com.example.pacetify.ui.playlists.songs

import android.content.DialogInterface
import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pacetify.MainActivity
import com.example.pacetify.R
import com.example.pacetify.data.Song
import com.example.pacetify.data.source.database.PacetifyDatabase
import com.example.pacetify.databinding.DialogFragmentSongsBinding
import com.example.pacetify.ui.playlists.PlaylistAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A dialogFragment for managing the songs of the playlist.
 */
class SongsDialogFragment(
    private val adapter: PlaylistAdapter,
    private val playlistName: String
): DialogFragment() {

    private var _binding: DialogFragmentSongsBinding? = null

    private val binding get() = _binding!!
    private lateinit var songs: MutableList<Song>

    private lateinit var mainActivity: MainActivity

    private var serviceManuallyStarted = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        mainActivity = requireActivity() as MainActivity

        isCancelable = true

        _binding = DialogFragmentSongsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val dao = PacetifyDatabase.getInstance(requireActivity()).pacetifyDao

        songs = mutableListOf()

        binding.tvNoSongs.text = if (songs.isEmpty()) getString(R.string.no_songs) else ""

        class SongAdapterDataObserver: RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                binding.tvNoSongs.text = if (songs.isEmpty()) getString(R.string.no_songs) else ""
            }
        }

        val adapter = SongAdapter(songs, dao, lifecycleScope, activity as MainActivity)
        binding.rvSongs.adapter = adapter
        adapter.registerAdapterDataObserver(SongAdapterDataObserver())
        binding.rvSongs.layoutManager = LinearLayoutManager(activity)

        lifecycleScope.launch {
            songs.addAll(dao.getSongsFromPlaylist(playlistName))
            adapter.notifyDataSetChanged()
        }

        // the option to add a song into the playlist
        binding.fabAddSong.setOnClickListener {
            AddSongDialog(
                requireActivity() as MainActivity, songs, lifecycle, playlistName
            ).apply {
                setOnDismissListener{
                    songs.removeAll { true }
                    lifecycleScope.launch {
                        delay(1000) // Wait for the song to load
                        songs.addAll(dao.getSongsFromPlaylist(playlistName))
                        adapter.notifyDataSetChanged()
                        binding.tvNoSongs.text = ""
                        if (mainActivity.serviceBoundFlow.value)
                            mainActivity.pacetifyService?.notifyPlaylistsChanged()
                    }
                }
                show()
            }
        }

        // Enabling playing the songs on tap:
        // First we stop the service clock ticking so we can play individual songs
        if (!mainActivity.serviceBoundFlow.value) {
            // If the service is not running, we want to start it to be able to use it
            mainActivity.startService(tick = false) //we do not want the service to start the clock
            mainActivity.bindService()
            // Remember that we started the service here
            serviceManuallyStarted = true
        } else {
            mainActivity.pacetifyService?.stopTicking()
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // If the service exists - it should - then start the clock again if it was running before
        // and stop it if it was not
        if (mainActivity.serviceBoundFlow.value)
            if (serviceManuallyStarted) {
                mainActivity.unbindService()
                mainActivity.stopService()
            }
            else mainActivity.pacetifyService?.startTicking()
    }

    override fun onResume() {
        super.onResume()
        // we need to reset the window attributes for the window to be displayed correctly
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