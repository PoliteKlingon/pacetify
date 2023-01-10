package com.example.myapplication.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.FragmentDashboardBinding
import com.example.myapplication.databinding.PlaylistBinding

class PlaylistAdapter(
    private var playlistURLs: MutableList<Playlist>
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
            }
        }
    }

    override fun getItemCount(): Int {
        return playlistURLs.size
    }
}