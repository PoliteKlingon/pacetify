package com.example.pacetify.ui.playlists.songs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.example.pacetify.MainActivity
import com.example.pacetify.data.Song
import com.example.pacetify.data.source.database.PacetifyDao
import com.example.pacetify.databinding.SongBinding
import kotlinx.coroutines.launch

/**
 * The adapter for every song in the songs recyclerView. It shows the song information,
 * and it can be deleted or played by clicking on it.
 */
class SongAdapter(
    private var songs: MutableList<Song>,
    private val dao: PacetifyDao,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val mainActivity: MainActivity
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
            tvArtistName.text = "${currentSong.artistName} (bpm: ${currentSong.bpm})"

            // display a dialog to ensure that the user wants to delete the song
            btnDelete.setOnClickListener {
                AlertDialog.Builder(mainActivity!!)
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
                        mainActivity.notifyServicePlaylists()
                        dialog.dismiss()
                    }
                    .show()
            }

            // Play the song on tap
            songView.setOnClickListener {
                if (mainActivity.serviceBoundFlow.value)
                    mainActivity.pacetifyService?.playSong(currentSong)
            }
        }
    }

    override fun getItemCount(): Int {
        return songs.size
    }
}