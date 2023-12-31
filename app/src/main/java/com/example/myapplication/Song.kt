package com.example.myapplication

import androidx.room.Entity

@Entity(primaryKeys = ["uri", "fromPlaylist"])
data class Song (
    val uri: String,
    val name: String,
    val artistName: String,
    val bpm: Int,
    val fromPlaylist: String,
    )
