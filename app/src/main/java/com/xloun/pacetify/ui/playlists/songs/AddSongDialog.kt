package com.xloun.pacetify.ui.playlists.songs

import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.xloun.pacetify.MainActivity
import com.xloun.pacetify.R
import com.xloun.pacetify.data.Song
import com.xloun.pacetify.util.NotConnectedException
import com.xloun.pacetify.util.UriUtils

/**
 * A dialog for adding a song. URL is required, checked and then the song is imported.
 *
 * Copyright (c) 2023 Jiří Loun
 * All rights reserved.
 */

class AddSongDialog(
    val mainActivity: MainActivity,
    val songs: MutableList<Song>,
    private val lifecycle: Lifecycle,
    private val playlistId: Long
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
        val btnPasteClipboard = findViewById<Button>(R.id.btnPasteClipboard)

        val clipboard = mainActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        btnPasteClipboard.setOnClickListener {
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val item = clipData.getItemAt(0)
                val clipboardText = item.text
                if (clipboardText != null) {
                    if (!UriUtils.isValidSpotifySongUri(clipboardText.toString()))
                        Toast.makeText(mainActivity, "Invalid song URL", Toast.LENGTH_LONG).show()
                    else {
                        etNewSongUri.setText(clipboardText.toString())

                        try {
                            //import song
                            mainActivity.webApi.addSongWithName(clipboardText.toString(), playlistId, lifecycle.coroutineScope)
                            mainActivity.notifyServicePlaylists(restartTicking = false)

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

        btnAddSong.setOnClickListener {
            val uri = etNewSongUri.text.toString()

            if (uri.isEmpty())
                Toast.makeText(mainActivity, "URL cannot be empty", Toast.LENGTH_LONG).show()
            else if (!UriUtils.isValidSpotifySongUri(uri))
                Toast.makeText(mainActivity, "Invalid song URL", Toast.LENGTH_LONG).show()
            else {try {
                    //import song
                    mainActivity.webApi.addSongWithName(uri, playlistId, lifecycle.coroutineScope)
                    mainActivity.notifyServicePlaylists(restartTicking = false)

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