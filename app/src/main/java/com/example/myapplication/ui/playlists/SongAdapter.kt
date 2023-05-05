package com.example.myapplication.ui.playlists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MainActivity
import com.example.myapplication.SRADao
import com.example.myapplication.Song
import com.example.myapplication.databinding.SongBinding
import kotlinx.coroutines.launch

class SongAdapter(
    private var songs: MutableList<Song>,
    private val dao: SRADao,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val activity: MainActivity?
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

            btnDelete.setOnClickListener {
                AlertDialog.Builder(activity!!)
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
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    override fun getItemCount(): Int {
        return songs.size
    }
}