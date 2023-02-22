package com.example.pacetify.ui.playlists.songs

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import com.example.pacetify.MainActivity
import com.example.pacetify.R
import com.example.pacetify.data.Song
import com.example.pacetify.util.UriUtils

class AddSongDialog(
    val activity: Activity,
    val songs: MutableList<Song>,
    val lifecycle: Lifecycle,
    val playlistName: String
): Dialog(activity){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_add_song)

        val etNewSongUri = findViewById<EditText>(R.id.etNewSongUri)
        val btnAddSong = findViewById<Button>(R.id.btnAddSong)

        btnAddSong.setOnClickListener {
            val uri = etNewSongUri.text.toString()

            if (uri.isEmpty())
                Toast.makeText(activity, "URL can not be empty", Toast.LENGTH_LONG).show()
            else if (!UriUtils.isValidSpotifySongUri(uri))
                Toast.makeText(activity, "Invalid song URL", Toast.LENGTH_LONG).show()
            else if (((activity as MainActivity?)?.isTokenAcquired()) != true) //is null or false
                Toast.makeText(activity, "Please connect to the internet to add a playlist", Toast.LENGTH_LONG).show()
            else {
                var id = uri.takeLastWhile { ch -> ch != '/' }
                if (id.contains('?')) {
                    id = id.takeWhile { ch -> ch != '?' }
                }

                (activity as MainActivity?)?.addSongWithName(id, playlistName)

                dismiss()
            }
        }
    }
}