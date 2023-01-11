package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Playlist(
    val uri: String,
    @PrimaryKey(autoGenerate = false)
    val name: String
)