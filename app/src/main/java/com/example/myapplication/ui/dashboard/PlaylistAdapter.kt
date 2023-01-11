package com.example.myapplication.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.Playlist
import com.example.myapplication.SRADao
import com.example.myapplication.SRADatabase
import com.example.myapplication.databinding.PlaylistBinding
import kotlinx.coroutines.launch

class PlaylistAdapter(
    private var playlistURLs: MutableList<Playlist>,
    private val dao: SRADao,
    private val lifecycleScope: LifecycleCoroutineScope
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
            btnDelete.setOnClickListener {
                playlistURLs.remove(currentPlaylist)
                this@PlaylistAdapter.notifyDataSetChanged()
                lifecycleScope.launch {
                    dao.deletePlaylist(currentPlaylist.name)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return playlistURLs.size
    }
}