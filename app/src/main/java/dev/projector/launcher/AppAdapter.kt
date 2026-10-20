package dev.projector.launcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** A horizontal row of rounded app tiles. [large] tiles are used for the dock's favorites row. */
class AppAdapter(
    private val large: Boolean,
    private val onClick: (AppEntry) -> Unit,
    private val onMenu: (AppEntry) -> Unit,
    private val onFocus: (AppEntry) -> Unit,
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    private var items: List<AppEntry> = emptyList()

    fun submit(newItems: List<AppEntry>) {
        val unchanged = newItems.map { it.key } == items.map { it.key }
        items = newItems
        // Skipping the rebind when nothing moved keeps D-pad focus where the user left it.
        if (!unchanged) notifyDataSetChanged()
    }

    fun indexOf(key: String): Int = items.indexOfFirst { it.key == key }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: View = view.findViewById(R.id.card)
        private val banner: ImageView = view.findViewById(R.id.banner)
        private val icon: ImageView = view.findViewById(R.id.icon)
        private val label: TextView = view.findViewById(R.id.label)
        private val density = view.resources.displayMetrics.density
        private var app: AppEntry? = null

        init {
            card.layoutParams.width = (density * if (large) 208 else 184).toInt()
            card.layoutParams.height = (density * if (large) 130 else 115).toInt()
            val iconSize = (density * if (large) 72 else 60).toInt()
            icon.layoutParams.width = iconSize
            icon.layoutParams.height = iconSize

            card.clipToOutline = true
            view.setOnClickListener { app?.let(onClick) }
            view.setOnLongClickListener {
                app?.let(onMenu)
                true
            }
            view.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_MENU) {
                    if (event.action == KeyEvent.ACTION_UP) app?.let(onMenu)
                    true
                } else {
                    false
                }
            }
            view.setOnFocusChangeListener { _, hasFocus ->
                showFocus(hasFocus, animate = true)
                if (hasFocus) app?.let(onFocus)
            }
        }

        fun bind(entry: AppEntry) {
            app = entry
            itemView.contentDescription = entry.label
            label.text = entry.label
            if (entry.banner != null) {
                banner.setImageDrawable(entry.banner)
                banner.visibility = View.VISIBLE
                icon.visibility = View.GONE
                card.background = cardBackground(BANNER_BACKGROUND, BANNER_BACKGROUND)
            } else {
                icon.setImageDrawable(entry.icon)
                banner.visibility = View.GONE
                icon.visibility = View.VISIBLE
                card.background = cardBackground(darken(entry.accent, 0.8f), darken(entry.accent, 0.35f))
            }
            showFocus(itemView.isFocused, animate = false)
        }

        private fun cardBackground(start: Int, end: Int) =
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply {
                cornerRadius = density * 18
            }

        // Like tvOS: the focused tile lifts and grows, and its name appears underneath.
        private fun showFocus(focused: Boolean, animate: Boolean) {
            val scale = if (focused) 1.14f else 1f
            val labelAlpha = if (focused) 1f else 0f
            card.translationZ = if (focused) density * 16 else 0f
            if (animate) {
                card.animate()
                    .scaleX(scale).scaleY(scale)
                    .setInterpolator(if (focused) OvershootInterpolator(2f) else DecelerateInterpolator())
                    .setDuration(220)
                    .start()
                label.animate().alpha(labelAlpha).setDuration(180).start()
            } else {
                card.animate().cancel()
                label.animate().cancel()
                card.scaleX = scale
                card.scaleY = scale
                label.alpha = labelAlpha
            }
        }
    }

    private companion object {
        const val BANNER_BACKGROUND = 0xFF1A1A24.toInt()

        fun darken(color: Int, factor: Float): Int = Color.rgb(
            (Color.red(color) * factor).toInt(),
            (Color.green(color) * factor).toInt(),
            (Color.blue(color) * factor).toInt(),
        )
    }
}
