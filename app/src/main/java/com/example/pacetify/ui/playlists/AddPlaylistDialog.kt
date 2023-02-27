package com.example.pacetify.ui.playlists

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
import com.example.pacetify.data.Playlist
import com.example.pacetify.data.source.database.PacetifyDao
import com.example.pacetify.util.UriUtils
import kotlinx.coroutines.launch

/**
 * A dialog for adding a playlist. Name and URL are required, checked and then the playlist and
 * its songs are imported.
 */
class AddPlaylistDialog(
    private val mainActivity: MainActivity,
    val playlists: MutableList<Playlist>,
    private val dao: PacetifyDao,
    private val adapter: PlaylistAdapter,
    val lifecycle: Lifecycle
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
                Toast.makeText(mainActivity, "Name can not be empty", Toast.LENGTH_LONG).show()
            else if (uri.isEmpty())
                Toast.makeText(mainActivity, "URL can not be empty", Toast.LENGTH_LONG).show()
            else if (playlists.map { p -> p.name } .contains(name))
                Toast.makeText(mainActivity, "Playlist \"$name\" already exists", Toast.LENGTH_LONG).show()
            else if (!UriUtils.isValidSpotifyPlaylistUri(uri))
                Toast.makeText(mainActivity, "Invalid playlist URL", Toast.LENGTH_LONG).show()
            else if (((mainActivity as MainActivity?)?.webApi?.isTokenAcquired()) != true) //is null or false
                Toast.makeText(mainActivity, "Please connect to the internet to add a playlist", Toast.LENGTH_LONG).show()
            else {
                val id = UriUtils.extractIdFromUri(uri)
                val playlist = Playlist(id, name)

                // import songs form the playlist
                (mainActivity as MainActivity?)?.webApi?.addSongsFromPlaylist(playlist, lifecycle.coroutineScope)

                playlists.add(playlist)
                adapter.notifyItemInserted(playlists.size - 1)
                etNewPlaylistName.setText("")
                etNewPlaylistUri.setText("")

                lifecycle.coroutineScope.launch {
                    dao.insertPlaylist(playlist)
                    mainActivity.notifyServicePlaylists()
                }

                dismiss()
            }
        }
    }
}