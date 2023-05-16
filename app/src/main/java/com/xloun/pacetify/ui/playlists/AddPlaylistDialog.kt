package com.xloun.pacetify.ui.playlists

import android.app.Dialog
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.xloun.pacetify.MainActivity
import com.xloun.pacetify.R
import com.xloun.pacetify.data.Playlist
import com.xloun.pacetify.data.source.database.PacetifyDao
import com.xloun.pacetify.util.NotConnectedException
import com.xloun.pacetify.util.UriUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A dialog for adding a playlist. Name and URL are required, checked and then the playlist and
 * its songs are imported.
 *
 * Copyright (c) 2023 Jiří Loun
 * All rights reserved.
 */
class AddPlaylistDialog(
    private val mainActivity: MainActivity,
    val playlists: MutableList<Playlist>,
    private val dao: PacetifyDao,
    private val adapter: PlaylistAdapter,
    private val lifecycle: Lifecycle
): Dialog(mainActivity){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_add_playlist)

        // references to the view elements
        val etNewPlaylistName = findViewById<EditText>(R.id.etNewPlaylistName)
        val etNewPlaylistUri = findViewById<EditText>(R.id.etNewPlaylistUri)
        val btnAddPlaylist = findViewById<Button>(R.id.btnAddPlaylist)

        btnAddPlaylist.setOnClickListener {
            val name = etNewPlaylistName.text.toString()
            val uri = etNewPlaylistUri.text.toString()

            if (name.isEmpty())
                Toast.makeText(mainActivity, "Name cannot be empty", Toast.LENGTH_LONG).show()
            else if (uri.isEmpty())
                Toast.makeText(mainActivity, "URL cannot be empty", Toast.LENGTH_LONG).show()
            else if (playlists.map { p -> p.name } .contains(name))
                Toast.makeText(mainActivity, "Playlist \"$name\" already exists", Toast.LENGTH_LONG).show()
            else if (!UriUtils.isValidSpotifyPlaylistUri(uri) && !UriUtils.isValidSpotifyAlbumUri(uri))
                Toast.makeText(mainActivity, "Invalid playlist/album URL", Toast.LENGTH_LONG).show()
            else {
                val id = UriUtils.extractIdFromUri(uri)
                val isAlbum = UriUtils.isValidSpotifyAlbumUri(uri)
                val playlist = Playlist(id, name, enabled = true, isAlbum = isAlbum)

                // import songs from the playlist
                try {
                    lifecycle.coroutineScope.launch {
                        val newId: Long = dao.insertPlaylist(playlist)
                        playlist.id = newId
                        mainActivity.webApi.addSongsFromPlaylist(playlist, lifecycle.coroutineScope, isAlbum)

                        mainActivity.notifyServicePlaylists()
                    }

                    playlists.add(playlist)
                    adapter.notifyItemInserted(playlists.size - 1)
                    etNewPlaylistName.setText("")
                    etNewPlaylistUri.setText("")

                    dismiss()
                }
                catch (e: NotConnectedException) {
                    Toast.makeText(
                        mainActivity,
                        "Please connect to the internet and try again",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}