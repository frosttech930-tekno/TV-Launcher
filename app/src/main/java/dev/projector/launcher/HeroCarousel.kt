package dev.projector.launcher

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.ExecutorService

/**
 * Full-screen movie backdrops that cross-fade every few seconds with a slow zoom,
 * plus the title, year/rating and overview of the movie on screen.
 */
class HeroCarousel(
    private val activity: Activity,
    private val front: ImageView,
    private val back: ImageView,
    private val title: TextView,
    private val meta: TextView,
    private val overview: TextView,
    private val executor: ExecutorService,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val advance = Runnable { showNext() }
    private var movies: List<Movie> = emptyList()
    private var index = -1
    private var running = false
    // Bumped whenever the schedule restarts, so image loads from an older schedule are ignored.
    private var generation = 0
    private var showing = front
    private var zoom: ObjectAnimator? = null

    fun setMovies(newMovies: List<Movie>) {
        if (newMovies.map { it.backdropUrl } == movies.map { it.backdropUrl }) return
        movies = newMovies
        index = -1
        if (running) showNext()
    }

    fun hasMovies(): Boolean = movies.isNotEmpty()

    fun start() {
        if (running) return
        running = true
        showNext()
    }

    fun stop() {
        running = false
        generation++
        handler.removeCallbacks(advance)
        zoom?.cancel()
    }

    private fun showNext() {
        handler.removeCallbacks(advance)
        if (!running || movies.isEmpty()) return
        val gen = ++generation
        val next = (index + 1) % movies.size
        val movie = movies[next]
        executor.execute {
            val bitmap = MovieRepository.loadBackdrop(activity, movie)
            handler.post {
                if (gen != generation || activity.isDestroyed) return@post
                index = next
                if (bitmap != null) display(movie, bitmap)
                // A failed download (e.g. offline) just moves on to the next movie sooner.
                handler.postDelayed(advance, if (bitmap != null) SLIDE_MS else RETRY_MS)
            }
        }
    }

    private fun display(movie: Movie, bitmap: Bitmap) {
        val incoming = if (showing === front) back else front
        val outgoing = showing
        showing = incoming

        incoming.animate().cancel()
        incoming.setImageBitmap(bitmap)
        incoming.alpha = 0f
        incoming.animate().alpha(1f).setDuration(FADE_MS).start()
        outgoing.animate().alpha(0f).setDuration(FADE_MS).withEndAction { outgoing.setImageDrawable(null) }.start()

        zoom?.cancel()
        zoom = ObjectAnimator.ofPropertyValuesHolder(
            incoming,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, ZOOM),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, ZOOM),
        ).apply {
            duration = SLIDE_MS + FADE_MS
            interpolator = LinearInterpolator()
            start()
        }

        val texts = listOf(title, meta, overview)
        texts.forEach { it.animate().cancel() }
        title.animate().alpha(0f).setDuration(TEXT_FADE_MS).withEndAction {
            title.text = movie.title
            meta.text = metaLine(movie)
            overview.text = movie.overview
            texts.forEach { it.animate().alpha(1f).setDuration(TEXT_FADE_MS).start() }
        }.start()
        meta.animate().alpha(0f).setDuration(TEXT_FADE_MS).start()
        overview.animate().alpha(0f).setDuration(TEXT_FADE_MS).start()
    }

    private fun metaLine(movie: Movie): String {
        val parts = mutableListOf<String>()
        if (movie.year.isNotEmpty()) parts += movie.year
        if (movie.rating > 0) parts += "★ " + String.format(Locale.US, "%.1f", movie.rating)
        parts += activity.getString(R.string.now_playing)
        return parts.joinToString("  ·  ")
    }

    private companion object {
        const val SLIDE_MS = 8_000L
        const val FADE_MS = 1_200L
        const val TEXT_FADE_MS = 250L
        const val RETRY_MS = 3_000L
        const val ZOOM = 1.08f
    }
}
