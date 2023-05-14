package com.xloun.pacetify.ui.playlists.songs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.xloun.pacetify.MainActivity
import com.xloun.pacetify.R
import com.xloun.pacetify.data.Song
import com.xloun.pacetify.data.source.database.PacetifyDao
import com.xloun.pacetify.databinding.SongBinding
import kotlinx.coroutines.launch

/**
 * The adapter for every song in the songs recyclerView. It shows the song information,
 * and it can be deleted or played by clicking on it.
 *
 * author: Jiří Loun
 */

class SongAdapter(
    private var songs: MutableList<Song>,
    private val dao: PacetifyDao,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val mainActivity: MainActivity,
    private val parentDialogFragment: SongsDialogFragment
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>()
{
    class SongViewHolder(val binding: SongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        return SongViewHolder(
            SongBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val currentSong = songs[position]
        holder.binding.apply {
            tvSongName.text = currentSong.name
            tvArtistName.text = "${currentSong.artistName} (${currentSong.bpm} BPM)"
            // The icon should be play if the song is not playing and pause if it is
            if (currentSong == parentDialogFragment.currentlyPlayingSong)
                icPlayPause.setImageResource(R.drawable.ic_pause_24)
            else
                icPlayPause.setImageResource(R.drawable.ic_play_24)

            // display a dialog to ensure that the user wants to delete the song
            btnDelete.setOnClickListener {
                AlertDialog.Builder(mainActivity)
                    .setTitle("Are you sure?")
                    .setMessage("Do you want to delete \"${currentSong.name}\"?")
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setPositiveButton("Delete")  { dialog, _ ->
                        songs.remove(currentSong)
                        this@SongAdapter.notifyDataSetChanged()
                        lifecycleScope.launch {
                            dao.deleteSong(currentSong)
                        }
                        mainActivity.notifyServicePlaylists(restartTicking = false)
                        dialog.dismiss()
                    }
                    .show()
            }

            // Play/Pause the song on tap
            songView.setOnClickListener {
                // Change icon of this view on click
                if (mainActivity.serviceBoundFlow.value) {
                    if (currentSong == parentDialogFragment.currentlyPlayingSong)
                        icPlayPause.setImageResource(R.drawable.ic_play_24)
                    else
                        icPlayPause.setImageResource(R.drawable.ic_pause_24)
                }

                // Handle last playing song icon and actually play/pause the song
                parentDialogFragment.playSong(currentSong, icPlayPause)
            }
        }
    }

    override fun getItemCount(): Int {
        return songs.size
    }
}