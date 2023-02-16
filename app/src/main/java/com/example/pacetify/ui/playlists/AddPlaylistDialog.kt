package com.example.pacetify.ui.playlists

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.example.pacetify.MainActivity
import com.example.pacetify.R
import com.example.pacetify.data.PacetifyService
import com.example.pacetify.data.Playlist
import com.example.pacetify.data.source.database.PacetifyDao
import com.example.pacetify.util.UriUtils
import kotlinx.coroutines.launch

class AddPlaylistDialog(
    val activity: Activity,
    val playlists: MutableList<Playlist>,
    val dao: PacetifyDao,
    val adapter: PlaylistAdapter,
    val serviceBound: Boolean,
    val pacetifyService: PacetifyService?,
    val lifecycle: Lifecycle
): Dialog(activity){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_add_playlist)

        val etNewPlaylistName = findViewById<EditText>(R.id.etNewPlaylistName)
        val etNewPlaylistUri = findViewById<EditText>(R.id.etNewPlaylistUri)
        val btnAddPlaylist = findViewById<Button>(R.id.btnAddPlaylist)

        btnAddPlaylist.setOnClickListener {
            val name = etNewPlaylistName.text.toString()
            val uri = etNewPlaylistUri.text.toString()

            if (name.isEmpty())
                Toast.makeText(activity, "Name can not be empty", Toast.LENGTH_LONG).show()
            else if (uri.isEmpty())
                Toast.makeText(activity, "URL can not be empty", Toast.LENGTH_LONG).show()
            else if (playlists.map { p -> p.name } .contains(name))
                Toast.makeText(activity, "Playlist \"$name\" already exists", Toast.LENGTH_LONG).show()
            else if (!UriUtils.isValidSpotifyPlaylistUri(uri))
                Toast.makeText(activity, "Invalid playlist URL", Toast.LENGTH_LONG).show()
            else if (((activity as MainActivity?)?.isTokenAcquired()) != true) //is null or false
                Toast.makeText(activity, "Please connect to the internet to add a playlist", Toast.LENGTH_LONG).show()
            else {
                var id = uri.takeLastWhile { ch -> ch != '/' }
                if (id.contains('?')) {
                    id = id.takeWhile { ch -> ch != '?' }
                }

                val playlist = Playlist(id, name)

                (activity as MainActivity?)?.addSongsFromPlaylist(playlist)

                playlists.add(playlist)
                adapter.notifyItemInserted(playlists.size - 1)
                etNewPlaylistName.setText("")
                etNewPlaylistUri.setText("")


                lifecycle.coroutineScope.launch {
                    dao.insertPlaylist(playlist)
                    if (serviceBound) pacetifyService?.notifyPlaylistsChanged()
                }

                dismiss()
            }
        }
    }
}