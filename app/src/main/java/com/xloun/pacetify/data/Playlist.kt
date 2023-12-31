package com.xloun.pacetify.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * author: Jiří Loun
 */

@Entity
data class Playlist(
    val id: String,
    @PrimaryKey(autoGenerate = false)
    val name: String,
    var enabled: Boolean
)
