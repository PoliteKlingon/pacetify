package com.example.pacetify.data

import androidx.room.Entity

/**
 * author: Jiří Loun
 */

@Entity(primaryKeys = ["uri", "fromPlaylist"])
data class Song (
    val uri: String,
    val name: String,
    val artistName: String,
    val bpm: Int,
    val fromPlaylist: String,
)
