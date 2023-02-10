package com.example.myapplication.ui.songs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.SRADao
import com.example.myapplication.Song
import com.example.myapplication.databinding.SongBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SongsAdapter(
    private var songs: Array<Song>,
    private val dao: SRADao,
    private val lifecycleScope: LifecycleCoroutineScope,
) :RecyclerView.Adapter<SongsAdapter.SongsViewHolder>()
{
    class SongsViewHolder(val binding: SongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongsViewHolder {
        return SongsViewHolder(
            SongBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: SongsViewHolder, position: Int) {
        val currentSong = songs[position]
        holder.binding.apply {
            tvSongName.text = "${currentSong.name} (bpm: ${currentSong.bpm})"
            tvArtistName.text = currentSong.artistName

            btnDelete.setOnClickListener {
                lifecycleScope.launch {
                    dao.deleteSong(currentSong)
                    songs = dao.getSongsWithDuplicates()
                    this@SongsAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return songs.size
    }
}