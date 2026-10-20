package dev.projector.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Movie(
    val title: String,
    val overview: String,
    val year: String,
    val rating: Double,
    val backdropUrl: String,
)

/** Loads "now playing" movies from TMDB, keeping the last good response for offline start-up. */
object MovieRepository {

    private const val NOW_PLAYING = "https://api.themoviedb.org/3/movie/now_playing?language=en-US&page=1"
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w1280"
    private const val CACHE_FILE = "now_playing.json"
    private const val MAX_MOVIES = 12
    private const val BACKDROP_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    fun cached(context: Context): List<Movie> = try {
        parse(File(context.filesDir, CACHE_FILE).readText())
    } catch (e: Exception) {
        emptyList()
    }

    fun cacheAgeMs(context: Context): Long {
        val file = File(context.filesDir, CACHE_FILE)
        return if (file.exists()) System.currentTimeMillis() - file.lastModified() else Long.MAX_VALUE
    }

    /**
     * Fetches the current list. Accepts either a v3 API key or a v4 read access token,
     * since TMDB's settings page shows both. Throws on network or auth failure.
     */
    fun fetch(context: Context, key: String): List<Movie> {
        val isToken = key.length > 40
        val url = if (isToken) NOW_PLAYING else "$NOW_PLAYING&api_key=${URLEncoder.encode(key, "UTF-8")}"
        val connection = open(url)
        if (isToken) connection.setRequestProperty("Authorization", "Bearer $key")
        connection.setRequestProperty("Accept", "application/json")
        val body = try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("TMDB returned HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val movies = parse(body)
        if (movies.isNotEmpty()) File(context.filesDir, CACHE_FILE).writeText(body)
        pruneBackdrops(context)
        return movies
    }

    /** Returns the backdrop from the disk cache, downloading it first if needed. Null on failure. */
    fun loadBackdrop(context: Context, movie: Movie): Bitmap? = try {
        val dir = File(context.cacheDir, "backdrops").apply { mkdirs() }
        val file = File(dir, Integer.toHexString(movie.backdropUrl.hashCode()) + ".jpg")
        if (!file.exists()) {
            val partial = File(dir, file.name + ".part")
            val connection = open(movie.backdropUrl)
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Image returned HTTP ${connection.responseCode}")
                }
                connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
            } finally {
                connection.disconnect()
            }
            partial.renameTo(file)
        }
        // RGB_565 halves the memory of each full-screen image; this projector only has 1 GB of RAM.
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
    } catch (e: Exception) {
        null
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
    }

    private fun parse(json: String): List<Movie> {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val movies = mutableListOf<Movie>()
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            // optString turns JSON null into the string "null".
            val backdrop = item.optString("backdrop_path")
            if (backdrop.isEmpty() || backdrop == "null") continue
            movies += Movie(
                title = item.optString("title"),
                overview = item.optString("overview").takeUnless { it == "null" }.orEmpty(),
                year = item.optString("release_date").take(4),
                rating = item.optDouble("vote_average", 0.0),
                backdropUrl = IMAGE_BASE + backdrop,
            )
            if (movies.size == MAX_MOVIES) break
        }
        return movies
    }

    private fun pruneBackdrops(context: Context) {
        val cutoff = System.currentTimeMillis() - BACKDROP_MAX_AGE_MS
        File(context.cacheDir, "backdrops").listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }
}
