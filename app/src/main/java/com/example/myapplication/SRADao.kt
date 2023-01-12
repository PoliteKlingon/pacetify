package com.example.myapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SRADao {
    @Insert(entity = Playlist::class, onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insertPlaylist(playlist: Playlist)

    @Insert(entity = Song::class, onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insertSong(song: Song)

    @Query("SELECT * FROM Song GROUP BY id ORDER BY bpm ASC")
    suspend fun getSongs(): List<Song>

    @Query("SELECT * FROM Playlist")
    suspend fun getPlaylists(): List<Playlist>

    @Query("DELETE FROM Song WHERE fromPlaylist = :playlistName")
    suspend fun _deletePlaylistSongs(playlistName: String)

    @Query("DELETE FROM Playlist WHERE name = :playlistName")
    suspend fun _deletePlaylistPlaylist(playlistName: String)

    @Transaction
    suspend fun deletePlaylist(playlistName: String) {
        _deletePlaylistSongs(playlistName)
        _deletePlaylistPlaylist(playlistName)
    }

    @Transaction
    suspend fun addPlaylist(playlist: Playlist) {
        _insertPlaylist(playlist)
        /*for song in playlist    TODO!!!
            _insertSong(song)
        */
    }
}