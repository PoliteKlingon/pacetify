package com.example.myapplication.ui.playlists

import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.MainActivity
import com.example.myapplication.Playlist
import com.example.myapplication.SRADao
import com.example.myapplication.databinding.PlaylistBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlaylistAdapter(
    private var playlistURLs: MutableList<Playlist>,
    private val dao: SRADao,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val activity: MainActivity?
) :RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>()
{
    class PlaylistViewHolder(val binding: PlaylistBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        return PlaylistViewHolder(
            PlaylistBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val currentPlaylist = playlistURLs[position]
        holder.binding.apply {
            tvPlaylistName.text = currentPlaylist.name
            lifecycleScope.launch  {
                val songNum = dao.getSongsNumInPlaylist(currentPlaylist.name)
                tvPlaylistSongs.text = if (songNum == 0) "Empty or invalid playlist" else "$songNum songs imported"
            }

            btnDelete.setOnClickListener {
                AlertDialog.Builder(activity!!)
                    .setTitle("Are you sure?")
                    .setMessage("Do you want to delete \"${currentPlaylist.name}\"?")
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setPositiveButton("Delete")  { dialog, _ ->
                        playlistURLs.remove(currentPlaylist)
                        this@PlaylistAdapter.notifyDataSetChanged()
                        lifecycleScope.launch {
                            dao.deletePlaylist(currentPlaylist.name)
                        }
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    override fun getItemCount(): Int {
        return playlistURLs.size
    }
}