package com.xloun.pacetify.data.source.database

import androidx.room.*
import com.xloun.pacetify.data.Playlist
import com.xloun.pacetify.data.Song

/**
 * The data access object for the Pacetify database
 *
 * Copyright (c) 2023 Jiří Loun
 * All rights reserved.
 */

@Dao
interface PacetifyDao {
    @Insert(entity = Playlist::class, onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

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
    @Query("SELECT * FROM Song INNER JOIN Playlist ON Playlist.id = Song.fromPlaylist WHERE Playlist.enabled = true GROUP BY uri ORDER BY bpm ASC")
    suspend fun getEnabledSongsDistinct(): Array<Song>

    // get the count of distinct songs from all enabled playlists
    @Query("SELECT COUNT(*) FROM Song INNER JOIN Playlist ON Playlist.id = Song.fromPlaylist WHERE Playlist.enabled = true")
    suspend fun numEnabledSongsDistinct(): Int

    @Query("SELECT * FROM Song ORDER BY name ASC")
    suspend fun getAllSongsWithDuplicates(): Array<Song>

    @Query("SELECT COUNT(*) FROM Song WHERE fromPlaylist = :playlistId")
    suspend fun getSongsNumInPlaylist(playlistId: Long): Int

    @Query("SELECT * FROM Song WHERE fromPlaylist = :playlistId ORDER BY name")
    suspend fun getSongsFromPlaylist(playlistId: Long): Array<Song>

    @Query("SELECT * FROM Playlist")
    suspend fun getPlaylists(): List<Playlist>

    @Query("SELECT * FROM Playlist WHERE id = :id")
    suspend fun getPlaylist(id: Long): Playlist

    @Query("SELECT COUNT(*) FROM Playlist")
    suspend fun numOfPlaylists(): Int

    // check if playlist exists - always returns 0 or 1
    @Query("SELECT COUNT(*) FROM Playlist WHERE id = :id")
    suspend fun existenceOfPlaylist(id: Long): Int

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