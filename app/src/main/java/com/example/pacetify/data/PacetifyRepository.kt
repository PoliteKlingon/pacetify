package com.example.pacetify.data

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.pacetify.MainActivity
import com.example.pacetify.data.source.database.PacetifyDatabase
import com.example.pacetify.data.source.preferenceFiles.SettingsPreferenceFile
import com.example.pacetify.data.source.spotify.WebApi

class PacetifyRepository(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: PacetifyRepository? = null

        // make this class a singleton
        fun getInstance(context: Context): PacetifyRepository {
            synchronized(this) {
                return INSTANCE ?: PacetifyRepository(context).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val dao = PacetifyDatabase.getInstance(context).pacetifyDao
    private val settingsPrefFile = SettingsPreferenceFile.getInstance(context)
    //private val webApi = WebApi.getInstance(context)

    suspend fun insertPlaylist(playlist: Playlist) = dao.insertPlaylist(playlist)

    suspend fun insertSong(song: Song) = dao.insertSong(song)

    suspend fun getSongs(): Array<Song> =  dao.getSongs()

    suspend fun getSongsWithDuplicates(): Array<Song> = dao.getSongsWithDuplicates()

    suspend fun getSongsNumInPlaylist(playlistName: String): Int =
        dao.getSongsNumInPlaylist(playlistName)

    suspend fun getSongsFromPlaylist(playlistName: String): Array<Song> =
        dao.getSongsFromPlaylist(playlistName)

    suspend fun getPlaylists(): List<Playlist> = dao.getPlaylists()

    suspend fun numOfPlaylists(name: String): Int = dao.numOfPlaylists(name)

    suspend fun deletePlaylist(playlistName: String) = dao.deletePlaylist(playlistName)

    suspend fun deleteSong(song: Song) = dao.deleteSong(song)

    var settingsMotivate
        get() = settingsPrefFile.motivate
        set(value) { settingsPrefFile.motivate = value }

    var settingsRest
        get() = settingsPrefFile.rest
        set(value) { settingsPrefFile.rest = value }

    var settingsRestTime
        get() = settingsPrefFile.restTime
        set(value) { settingsPrefFile.restTime = value }

    /*fun onDestroy() = webApi.onDestroy()
    //TODO this should be resolved and removed

    fun isNetworkBeingUsed(): Boolean = webApi.isNetworkBeingUsed()

    fun addSongsFromPlaylist(playlist: Playlist, lifecycleScope: LifecycleCoroutineScope) =
        webApi.addSongsFromPlaylist(playlist, lifecycleScope)

    fun addSongWithName(songId: String, playlistName: String, lifecycleScope: LifecycleCoroutineScope) =
        webApi.addSongWithName(songId, playlistName, lifecycleScope)*/
}