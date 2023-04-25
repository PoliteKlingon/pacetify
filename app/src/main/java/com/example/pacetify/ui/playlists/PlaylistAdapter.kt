package com.example.pacetify.ui.playlists

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.example.pacetify.MainActivity
import com.example.pacetify.data.Playlist
import com.example.pacetify.data.source.database.PacetifyDao
import com.example.pacetify.databinding.PlaylistBinding
import com.example.pacetify.ui.playlists.songs.SongsDialogFragment
import kotlinx.coroutines.launch

/**
 * This is the adapter for playlist recyclerView - each playlist is displayed with its song count,
 * it can be deleted or clicked on to manage its songs.
 */
class PlaylistAdapter(
    private var playlists: MutableList<Playlist>,
    private val dao: PacetifyDao,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val mainActivity: MainActivity,
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
        val currentPlaylist = playlists[position]
        holder.binding.apply {
            tvPlaylistName.text = currentPlaylist.name
            lifecycleScope.launch  {
                val songNum = dao.getSongsNumInPlaylist(currentPlaylist.name)
                tvPlaylistSongs.text = if (songNum == 0) "Empty or invalid playlist" else "$songNum songs imported (tap to manage)"
            }

            cbEnable.isChecked = currentPlaylist.enabled

            // I do not use setOnCheckedChangeListener because the recycler list might change it
            // while loading, I want this to happen only when it is clicked explicitly
            cbEnable.setOnClickListener {
                val isChecked = cbEnable.isChecked
                playlists[position].enabled = isChecked
                lifecycleScope.launch {
                    val updatedPlaylist = currentPlaylist.copy(enabled = isChecked)
                    dao.updatePlaylist(updatedPlaylist)
                    mainActivity.notifyServicePlaylists()
                }
            }

            // display dialog to ensure that the user really wanted to delete the playlist
            btnDelete.setOnClickListener {
                AlertDialog.Builder(mainActivity)
                    .setTitle("Are you sure?")
                    .setMessage("Do you want to delete \"${currentPlaylist.name}\"?")
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setPositiveButton("Delete")  { dialog, _ ->
                        playlists.remove(currentPlaylist)
                        Log.d("ASD", "deleting playlist ${currentPlaylist.name}")
                        this@PlaylistAdapter.notifyDataSetChanged()
                        lifecycleScope.launch {
                            dao.deletePlaylist(currentPlaylist.name)
                        }
                        mainActivity.notifyServicePlaylists()
                        dialog.dismiss()
                    }
                    .show()
            }

            // onClick display songs in the playlist as a dialogFragment
            clPlaylist.setOnClickListener {
                SongsDialogFragment(this@PlaylistAdapter, currentPlaylist.name, position)
                    .show(childFragmentManager, "")
            }
        }
    }

    override fun getItemCount(): Int {
        return playlists.size
    }
}