package com.example.pacetify.util

import android.webkit.URLUtil

class UriUtils {
    companion object Functions {
        fun isValidSpotifyPlaylistUri(uri: String) : Boolean {
            return URLUtil.isValidUrl(uri) &&
                    (uri.matches(Regex("https://open.spotify.com/playlist/[^/]*"))
                            || uri.matches(Regex("https://open.spotify.com/user/[^/]*/playlist/[^/]*")))
        }

        fun isValidSpotifySongUri(uri: String) : Boolean {
            return URLUtil.isValidUrl(uri) &&
                    (uri.matches(Regex("https://open.spotify.com/track/[^/]*"))
                            || uri.matches(Regex("https://open.spotify.com/user/[^/]*/track/[^/]*")))
        }

        fun extractIdFromUri(uri: String): String {
            var id = uri.takeLastWhile { ch -> ch != '/' && ch != ':' }
            if (id.contains('?')) {
                id = id.takeWhile { ch -> ch != '?' }
            }
            return id
        }
    }
}