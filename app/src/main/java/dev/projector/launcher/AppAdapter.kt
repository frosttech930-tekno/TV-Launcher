package dev.projector.launcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** A horizontal row of app cards. [large] cards are used for the "For you" row. */
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        val metrics = parent.resources.displayMetrics
        view.layoutParams.width = (metrics.density * if (large) 256 else 200).toInt()
        view.layoutParams.height = (metrics.density * if (large) 144 else 112).toInt()
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val banner: ImageView = view.findViewById(R.id.banner)
        private val iconGroup: View = view.findViewById(R.id.icon_group)
        private val icon: ImageView = view.findViewById(R.id.icon)
        private val label: TextView = view.findViewById(R.id.label)
        private val density = view.resources.displayMetrics.density
        private var app: AppEntry? = null

        init {
            val iconSize = (density * if (large) 64 else 48).toInt()
            icon.layoutParams.width = iconSize
            icon.layoutParams.height = iconSize
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (large) 17f else 15f)

            view.clipToOutline = true
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
            if (entry.banner != null) {
                banner.setImageDrawable(entry.banner)
                banner.visibility = View.VISIBLE
                iconGroup.visibility = View.GONE
                itemView.background = cardBackground(BANNER_BACKGROUND, BANNER_BACKGROUND)
            } else {
                icon.setImageDrawable(entry.icon)
                label.text = entry.label
                banner.visibility = View.GONE
                iconGroup.visibility = View.VISIBLE
                itemView.background = cardBackground(darken(entry.accent, 0.75f), darken(entry.accent, 0.3f))
            }
            showFocus(itemView.isFocused, animate = false)
        }

        private fun cardBackground(start: Int, end: Int) =
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply {
                cornerRadius = density * 16
            }

        private fun showFocus(focused: Boolean, animate: Boolean) {
            val scale = if (focused) 1.12f else 1f
            val alpha = if (focused) 1f else UNFOCUSED_ALPHA
            itemView.translationZ = if (focused) density * 12 else 0f
            if (animate) {
                itemView.animate()
                    .scaleX(scale).scaleY(scale).alpha(alpha)
                    .setInterpolator(if (focused) OvershootInterpolator(2f) else DecelerateInterpolator())
                    .setDuration(220)
                    .start()
            } else {
                itemView.animate().cancel()
                itemView.scaleX = scale
                itemView.scaleY = scale
                itemView.alpha = alpha
            }
        }
    }

    private companion object {
        const val BANNER_BACKGROUND = 0xFF1A1A24.toInt()
        const val UNFOCUSED_ALPHA = 0.8f

        fun darken(color: Int, factor: Float): Int = Color.rgb(
            (Color.red(color) * factor).toInt(),
            (Color.green(color) * factor).toInt(),
            (Color.blue(color) * factor).toInt(),
        )
    }
}
