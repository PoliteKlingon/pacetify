package com.xloun.pacetify.data.source.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.xloun.pacetify.data.Playlist
import com.xloun.pacetify.data.Song

/**
 * The Pacetify database - singleton class
 *
 * Copyright (c) 2023 Jiří Loun
 * All rights reserved.
 */

@Database(entities = [Playlist::class, Song::class], version = 1)
abstract class PacetifyDatabase :RoomDatabase() {
    abstract val pacetifyDao: PacetifyDao

    companion object {
        @Volatile
        private var INSTANCE: PacetifyDatabase? = null

        //make this class a singleton
        fun getInstance(context: Context): PacetifyDatabase {
            synchronized(this) {
                return INSTANCE ?: Room.databaseBuilder(
                    context,
                    PacetifyDatabase::class.java,
                    "pacetify_db"
                ).build().also {
                    INSTANCE = it
                }
            }
        }
    }
}