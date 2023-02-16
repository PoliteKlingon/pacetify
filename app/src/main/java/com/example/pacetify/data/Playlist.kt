package com.example.pacetify.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Playlist(
    val id: String,
    @PrimaryKey(autoGenerate = false)
    val name: String
)
