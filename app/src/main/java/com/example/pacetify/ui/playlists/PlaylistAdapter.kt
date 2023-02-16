package com.example.pacetify.ui.playlists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.example.pacetify.MainActivity
import com.example.pacetify.data.PacetifyService
import com.example.pacetify.data.Playlist
import com.example.pacetify.data.source.database.PacetifyDao
import com.example.pacetify.databinding.PlaylistBinding
import com.example.pacetify.ui.playlists.songs.SongsDialogFragment
import kotlinx.coroutines.launch

class PlaylistAdapter(
    private var playlistURLs: MutableList<Playlist>,
    private val dao: PacetifyDao,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val activity: MainActivity?,
    private val serviceBound: Boolean,
    private val sraService: PacetifyService?,
    private val childFragmentManager: FragmentManager
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
                tvPlaylistSongs.text = if (songNum == 0) "Empty or invalid playlist" else "$songNum songs imported (tap to manage)"
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

            clPlaylist.setOnClickListener {
                SongsDialogFragment(serviceBound, sraService, this@PlaylistAdapter, currentPlaylist.name)
                    .show(childFragmentManager, "")
            }
        }
    }

    override fun getItemCount(): Int {
        return playlistURLs.size
    }
}