package com.xloun.pacetify.ui.playlists.songs

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xloun.pacetify.MainActivity
import com.xloun.pacetify.R
import com.xloun.pacetify.data.Playlist
import com.xloun.pacetify.data.Song
import com.xloun.pacetify.data.source.database.PacetifyDatabase
import com.xloun.pacetify.databinding.DialogFragmentSongsBinding
import com.xloun.pacetify.ui.playlists.PlaylistAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * A dialogFragment for managing the songs of the playlist.
 *
 * Copyright (c) 2023 Jiří Loun
 * All rights reserved.
 */

class SongsDialogFragment(
    private val playlistAdapter: PlaylistAdapter,
    private val playlist: Playlist,
    private val position: Int
): DialogFragment() {

    private var _binding: DialogFragmentSongsBinding? = null
    private val binding get() = _binding!!
    private lateinit var mainActivity: MainActivity

    private var serviceManuallyStarted = false
    private lateinit var songs: MutableList<Song>
    var currentlyPlayingSong: Song? = null
    private var currentlyPlayingSongIcon: ImageView? = null

    // This function takes care of changing the last played song's icon to play, so there is always
    // only at most one song with pause icon at a time. It also does the song playing/pausing.
    fun playSong(song: Song, view: ImageView) {
        if (mainActivity.serviceBoundFlow.value) {
            currentlyPlayingSongIcon?.setImageResource(R.drawable.ic_play_24)
            if (song == currentlyPlayingSong) {
                mainActivity.pacetifyService?.pauseSong()
                currentlyPlayingSong = null
                currentlyPlayingSongIcon = null
            } else {
                mainActivity.pacetifyService?.playSong(song)
                currentlyPlayingSong = song
                currentlyPlayingSongIcon = view
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        isCancelable = true

        mainActivity = requireActivity() as MainActivity
        _binding = DialogFragmentSongsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val dao = PacetifyDatabase.getInstance(mainActivity).pacetifyDao
        songs = mutableListOf()

        binding.tvPlaylistName.text = playlist.name
        binding.etPlaylistName.text.append(playlist.name)
        binding.etPlaylistName.alpha = 0.0f
        binding.etPlaylistName.isEnabled = false
        binding.btnConfirmRename.isVisible = false

        binding.btnRename.setOnClickListener {
            binding.tvPlaylistName.alpha = 0.0f
            binding.etPlaylistName.alpha = 1.0f
            binding.etPlaylistName.isEnabled = true
            binding.btnRename.isEnabled = false
            binding.btnRename.alpha = 0.0f
            binding.btnConfirmRename.isVisible = true
        }

        binding.btnConfirmRename.setOnClickListener {
            binding.tvPlaylistName.alpha = 1.0f
            binding.etPlaylistName.alpha = 0.0f
            binding.etPlaylistName.isEnabled = false
            binding.btnRename.isEnabled = true
            binding.btnRename.alpha = 1.0f
            binding.btnConfirmRename.isVisible = false
            // TODO check name
            // TODO rename playlist
            // TODO rename songs in playlist
        }

        binding.btnLink.setOnClickListener {
            val intent =
                if (playlist.isAlbum)
                    Intent(Intent.ACTION_VIEW, Uri.parse("spotify:album:${playlist.id}"))
                else
                    Intent(Intent.ACTION_VIEW, Uri.parse("spotify:playlist:${playlist.id}"))
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(mainActivity, "Cannot open link", Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvNoSongs.text = if (songs.isEmpty()) getString(R.string.no_songs) else ""
        class SongAdapterDataObserver: RecyclerView.AdapterDataObserver() {
            // observe the adapter to react to changes
            override fun onChanged() { // generic change
                binding.tvNoSongs.text = if (songs.isEmpty()) getString(R.string.no_songs) else ""
            }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                binding.tvNoSongs.text = if (songs.isEmpty()) getString(R.string.no_songs) else ""
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                super.onItemRangeRemoved(positionStart, itemCount)
                binding.tvNoSongs.text = if (songs.isEmpty()) getString(R.string.no_songs) else ""
            }
        }

        val adapter = SongAdapter(songs, dao, lifecycleScope, activity as MainActivity, this)
        binding.rvSongs.adapter = adapter
        adapter.registerAdapterDataObserver(SongAdapterDataObserver())
        binding.rvSongs.layoutManager = LinearLayoutManager(activity)

        lifecycleScope.launch {
            songs.addAll(dao.getSongsFromPlaylist(playlist.name))
            adapter.notifyDataSetChanged()
        }

        // the option to add a song into the playlist
        binding.fabAddSong.setOnClickListener {
            AddSongDialog(
                mainActivity, songs, lifecycle, playlist.name
            ).apply {
                setOnDismissListener{
                    lifecycleScope.launch {
                        delay(1000) // Wait for the song to load
                        // I do not know where is the new song in the list, so I have to reload it
                        songs.removeAll { true }
                        songs.addAll(dao.getSongsFromPlaylist(playlistName))
                        adapter.notifyDataSetChanged()
                        binding.tvNoSongs.text = ""
                        if (mainActivity.serviceBoundFlow.value)
                            mainActivity.notifyServicePlaylists(restartTicking = false)
                    }
                }
                show()
            }
        }

        // Enabling playing the songs on tap:
        // First I stop the service clock ticking so I can play individual songs
        if (!mainActivity.serviceBoundFlow.value) {
            // If the service is not running, I want to start it to be able to use it
            mainActivity.startService(tick = false) //I do not want the service to start the clock
            mainActivity.bindService()
            // Remember that I started the service here
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
        // I need to reset the window attributes for the window to be displayed correctly
        val windowAttributes = dialog?.window?.attributes?.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
        dialog?.window?.attributes = windowAttributes
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // I could have added or deleted a song, so I update the list for it to have correct
        // song counts
        playlistAdapter.notifyItemChanged(position)
    }
}