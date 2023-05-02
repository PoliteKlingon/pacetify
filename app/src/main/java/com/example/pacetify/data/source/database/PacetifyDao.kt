package com.example.pacetify.data.source.database

import androidx.room.*
import com.example.pacetify.data.Playlist
import com.example.pacetify.data.Song

/**
 * The data access object for the Pacetify database
 *
 * author: Jiří Loun
 */

@Dao
interface PacetifyDao {
    @Insert(entity = Playlist::class, onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist)

    // safe insert
    @Transaction
    suspend fun insertSong(song: Song){
        //stop importing songs from a playlist that has already been deleted
        if (existenceOfPlaylist(song.fromPlaylist) > 0) {
            _insertSong(song)
        }
    }

    // unsafe insert - to be called internally only
    @Insert(entity = Song::class, onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insertSong(song: Song)

    // get distinct songs from all enabled playlists
    @Query("SELECT * FROM Song INNER JOIN Playlist ON Playlist.name = Song.fromPlaylist WHERE Playlist.enabled = true GROUP BY uri ORDER BY bpm ASC")
    suspend fun getEnabledSongsDistinct(): Array<Song>

    // get the count of distinct songs from all enabled playlists
    @Query("SELECT COUNT(*) FROM Song INNER JOIN Playlist ON Playlist.name = Song.fromPlaylist WHERE Playlist.enabled = true")
    suspend fun numEnabledSongsDistinct(): Int

    @Query("SELECT * FROM Song ORDER BY name ASC")
    suspend fun getAllSongsWithDuplicates(): Array<Song>

    @Query("SELECT COUNT(*) FROM Song WHERE fromPlaylist = :playlistName")
    suspend fun getSongsNumInPlaylist(playlistName: String): Int

    @Query("SELECT * FROM Song WHERE fromPlaylist = :playlistName ORDER BY name")
    suspend fun getSongsFromPlaylist(playlistName: String): Array<Song>

    @Query("SELECT * FROM Playlist")
    suspend fun getPlaylists(): List<Playlist>

    @Query("SELECT COUNT(*) FROM Playlist")
    suspend fun numOfPlaylists(): Int

    // check if playlist exists - always returns 0 or 1
    @Query("SELECT COUNT(*) FROM Playlist WHERE name = :name")
    suspend fun existenceOfPlaylist(name: String): Int

    @Query("SELECT COUNT(*) FROM Playlist WHERE enabled = true")
    suspend fun numOfEnabledPlaylists(): Int

    // delete songs from playlist - to be called internally only
    @Query("DELETE FROM Song WHERE fromPlaylist = :playlistName")
    suspend fun _deletePlaylistSongs(playlistName: String)

    //delete the playlist itself - to be called internally only
    @Query("DELETE FROM Playlist WHERE name = :playlistName")
    suspend fun _deletePlaylistPlaylist(playlistName: String)

    // safely delete a playlist and all its contents
    @Transaction
    suspend fun deletePlaylist(playlistName: String) {
        _deletePlaylistSongs(playlistName)
        _deletePlaylistPlaylist(playlistName)
    }

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deleteSong(song: Song)
}