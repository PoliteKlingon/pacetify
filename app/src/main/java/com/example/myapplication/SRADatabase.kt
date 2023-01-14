package com.example.myapplication

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Playlist::class, Song::class], version = 1)
abstract class SRADatabase :RoomDatabase() {
    abstract val SRADao: SRADao

    companion object {
        @Volatile
        private var INSTANCE: SRADatabase? = null

        fun getInstance(context: Context): SRADatabase {
            synchronized(this) {
                return INSTANCE ?: Room.databaseBuilder(
                    context,
                    SRADatabase::class.java,
                    "sra_db"
                ).build().also {
                    INSTANCE = it
                }
            }
        }
    }
}