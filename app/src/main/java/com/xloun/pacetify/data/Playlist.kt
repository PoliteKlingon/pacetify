package com.xloun.pacetify.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Copyright (c) 2023 Jiří Loun
 * All rights reserved.
 */

@Entity
data class Playlist(
    val spotifyId: String,
    val name: String,
    var enabled: Boolean,
    var isAlbum: Boolean
){
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}
