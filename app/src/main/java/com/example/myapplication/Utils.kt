package com.example.myapplication

import android.content.Context
import android.net.ConnectivityManager
import android.webkit.URLUtil
import androidx.core.content.ContextCompat.getSystemService


class Utils {
    companion object Functions {
        fun isInternetAvailable(): Boolean {
            return true //TODO!!!!!!!
        }

        fun isValidSpotifyPlaylistUri(uri: String) : Boolean {
            return URLUtil.isValidUrl(uri) &&
                    uri.matches(Regex("https://open.spotify.com/playlist/[a-zA-Z0-9?=]*"))
        }
    }
}