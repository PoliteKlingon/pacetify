package com.xloun.pacetify.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Copyright (c) 2023 Jiří Loun
 * All rights reserved.
 */

@Entity
data class Playlist(
    val id: String,
    @PrimaryKey(autoGenerate = false)
    val name: String,
    var enabled: Boolean,
    var isAlbum: Boolean
)
