package com.example.pacetify.data.source.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.pacetify.data.Playlist
import com.example.pacetify.data.Song

@Database(entities = [Playlist::class, Song::class], version = 1)
abstract class PacetifyDatabase :RoomDatabase() {
    abstract val PacetifyDao: PacetifyDao

    companion object {
        @Volatile
        private var INSTANCE: PacetifyDatabase? = null

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