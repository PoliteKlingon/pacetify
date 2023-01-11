package com.example.myapplication

import androidx.room.Entity

@Entity(primaryKeys = ["id", "fromPlaylist"])
data class Song (
    val id: Int,
    val bpm: Int,
    val fromPlaylist: String,
    )
