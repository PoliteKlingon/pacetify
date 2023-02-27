package com.example.pacetify.ui.playlists.songs

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
import com.example.pacetify.data.Song
import com.example.pacetify.util.UriUtils

/**
 * A dialog for adding a song. URL is required, checked and then the song is imported.
 */
class AddSongDialog(
    val mainActivity: MainActivity,
    val songs: MutableList<Song>,
    val lifecycle: Lifecycle,
    val playlistName: String
): Dialog(mainActivity){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_add_song)

        // references to the view elements
        val etNewSongUri = findViewById<EditText>(R.id.etNewSongUri)
        val btnAddSong = findViewById<Button>(R.id.btnAddSong)

        btnAddSong.setOnClickListener {
            val uri = etNewSongUri.text.toString()

            if (uri.isEmpty())
                Toast.makeText(mainActivity, "URL can not be empty", Toast.LENGTH_LONG).show()
            else if (!UriUtils.isValidSpotifySongUri(uri))
                Toast.makeText(mainActivity, "Invalid song URL", Toast.LENGTH_LONG).show()
            else if (!mainActivity.webApi.isTokenAcquired())
                Toast.makeText(mainActivity, "Please connect to the internet to add a playlist", Toast.LENGTH_LONG).show()
            else {
                val id = UriUtils.extractIdFromUri(uri)

                //import song
                mainActivity.webApi.addSongWithName(id, playlistName, lifecycle.coroutineScope)
                mainActivity.notifyServicePlaylists()

                dismiss()
            }
        }
    }
}