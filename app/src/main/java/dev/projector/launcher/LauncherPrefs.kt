package dev.projector.launcher

import android.content.Context
import java.security.SecureRandom

class LauncherPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** Ordered list of favorite package names. */
    var favorites: List<String>
        get() = prefs.getString(KEY_FAVORITES, "").orEmpty().split('\n').filter { it.isNotEmpty() }
        private set(value) = prefs.edit().putString(KEY_FAVORITES, value.joinToString("\n")).apply()

    /** TMDB API key or read access token for the movie carousel; empty when not set up. */
    var tmdbKey: String
        get() = prefs.getString(KEY_TMDB, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TMDB, value).apply()

    var remoteEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMOTE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_REMOTE_ENABLED, value).apply()

    /** Four-digit PIN the phone remote must send; created once so saved phone shortcuts keep working. */
    val remotePin: String
        get() = prefs.getString(KEY_REMOTE_PIN, null) ?: (1000 + SecureRandom().nextInt(9000)).toString().also {
            prefs.edit().putString(KEY_REMOTE_PIN, it).apply()
        }

    val hidden: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()

    fun addFavorite(key: String) {
        if (key !in favorites) favorites = favorites + key
    }

    fun removeFavorite(key: String) {
        favorites = favorites - key
    }

    /** Moves a favorite by [delta] slots and returns its new position, or -1 if it isn't a favorite. */
    fun moveFavorite(key: String, delta: Int): Int {
        val list = favorites.toMutableList()
        val from = list.indexOf(key)
        if (from < 0) return -1
        val to = (from + delta).coerceIn(0, list.size - 1)
        list.removeAt(from)
        list.add(to, key)
        favorites = list
        return to
    }

    fun setHidden(key: String, hide: Boolean) {
        val set = hidden.toMutableSet()
        if (hide) set.add(key) else set.remove(key)
        prefs.edit().putStringSet(KEY_HIDDEN, set).apply()
        if (hide) removeFavorite(key)
    }

    /** On first run, pins the popular streaming/media apps that happen to be installed. */
    fun seedFavoritesOnce(installed: Set<String>) {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        favorites = DEFAULT_FAVORITES.filter { it in installed }
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_HIDDEN = "hidden"
        const val KEY_SEEDED = "seeded"
        const val KEY_TMDB = "tmdb_key"
        const val KEY_REMOTE_ENABLED = "remote_enabled"
        const val KEY_REMOTE_PIN = "remote_pin"

        val DEFAULT_FAVORITES = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.plexapp.android",
            "org.xbmc.kodi",
            "org.videolan.vlc",
        )
    }
}
