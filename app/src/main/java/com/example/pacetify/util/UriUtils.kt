package com.example.pacetify.util

import android.webkit.URLUtil

/**
 * Util class for URI manipulation
 *
 * author: Jiří Loun
 */
class UriUtils {
    companion object Functions {
        fun isValidSpotifyPlaylistUri(uri: String) : Boolean {
            return URLUtil.isValidUrl(uri) &&
                    (uri.matches(Regex("https://open.spotify.com/playlist/[^/]*"))
                            || uri.matches(Regex("https://open.spotify.com/user/[^/]*/playlist/[^/]*")))
        }

        fun isValidSpotifyAlbumUri(uri: String) : Boolean {
            return URLUtil.isValidUrl(uri) &&
                    (uri.matches(Regex("https://open.spotify.com/album/[^/]*"))
                            || uri.matches(Regex("https://open.spotify.com/user/[^/]*/album/[^/]*")))
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