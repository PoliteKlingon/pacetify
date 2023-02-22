package com.example.pacetify.data.source.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.pacetify.data.Playlist
import com.example.pacetify.data.Song

@Dao
interface PacetifyDao {
    @Insert(entity = Playlist::class, onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist)

    @Transaction
    suspend fun insertSong(song: Song){
        //stop importing songs from a playlist that has already been deleted
        if (numOfPlaylists(song.fromPlaylist) > 0) {
            _insertSong(song)
        }
    }

    @Insert(entity = Song::class, onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insertSong(song: Song)

    @Query("SELECT * FROM Song GROUP BY uri ORDER BY bpm ASC")
    suspend fun getSongs(): Array<Song>

    @Query("SELECT * FROM Song ORDER BY name ASC")
    suspend fun getSongsWithDuplicates(): Array<Song>

    @Query("SELECT COUNT(*) FROM Song WHERE fromPlaylist = :playlistName")
    suspend fun getSongsNumInPlaylist(playlistName: String): Int

    @Query("SELECT * FROM Song WHERE fromPlaylist = :playlistName ORDER BY name")
    suspend fun getSongsFromPlaylist(playlistName: String): Array<Song>

    @Query("SELECT * FROM Playlist")
    suspend fun getPlaylists(): List<Playlist>

    @Query("SELECT COUNT(*) FROM Playlist WHERE name = :name")
    suspend fun numOfPlaylists(name: String): Int

    @Query("DELETE FROM Song WHERE fromPlaylist = :playlistName")
    suspend fun _deletePlaylistSongs(playlistName: String)

    @Query("DELETE FROM Playlist WHERE name = :playlistName")
    suspend fun _deletePlaylistPlaylist(playlistName: String)

    @Transaction
    suspend fun deletePlaylist(playlistName: String) {
        _deletePlaylistSongs(playlistName)
        _deletePlaylistPlaylist(playlistName)
    }

    @Delete
    suspend fun deleteSong(song: Song)
}