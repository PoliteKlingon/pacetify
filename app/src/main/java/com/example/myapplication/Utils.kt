package com.example.myapplication

import android.webkit.URLUtil


class Utils {
    companion object Functions {
        fun isValidSpotifyPlaylistUri(uri: String) : Boolean {
            return URLUtil.isValidUrl(uri) &&
                    (uri.matches(Regex("https://open.spotify.com/playlist/[^/]*"))
                            || uri.matches(Regex("https://open.spotify.com/user/[^/]*/playlist/[^/]*")))
        }
    }
}