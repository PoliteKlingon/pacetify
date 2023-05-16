package com.xloun.pacetify.data

import androidx.room.Entity

/**
 * Copyright (c) 2023 Jiří Loun
 * All rights reserved.
 */

@Entity(primaryKeys = ["uri", "fromPlaylist"])
data class Song (
    val uri: String,
    val name: String,
    val artistName: String,
    val bpm: Int,
    val fromPlaylist: Long,
)
