package dev.projector.launcher

import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
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
        private val banner: ImageView = view.findViewById(R.id.banner)
        private val iconGroup: View = view.findViewById(R.id.icon_group)
        private val icon: ImageView = view.findViewById(R.id.icon)
        private val label: TextView = view.findViewById(R.id.label)
        private var app: AppEntry? = null

        init {
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
            view.setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.1f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
                v.translationZ = if (hasFocus) v.resources.displayMetrics.density * 8 else 0f
                if (hasFocus) app?.let(onFocus)
            }
        }

        fun bind(entry: AppEntry) {
            app = entry
            itemView.contentDescription = entry.label
            val background = itemView.background.mutate() as GradientDrawable
            if (entry.banner != null) {
                banner.setImageDrawable(entry.banner)
                banner.visibility = View.VISIBLE
                iconGroup.visibility = View.GONE
                background.setColor(BANNER_BACKGROUND)
            } else {
                icon.setImageDrawable(entry.icon)
                label.text = entry.label
                banner.visibility = View.GONE
                iconGroup.visibility = View.VISIBLE
                background.setColor(CARD_COLORS[entry.packageName.hashCode().mod(CARD_COLORS.size)])
            }
        }
    }

    private companion object {
        const val BANNER_BACKGROUND = 0xFF1C1F2E.toInt()

        // Phone-style apps have no banner, so give each a stable color tile instead.
        val CARD_COLORS = intArrayOf(
            0xFF3949AB.toInt(),
            0xFF00897B.toInt(),
            0xFF8E24AA.toInt(),
            0xFFD81B60.toInt(),
            0xFF5E35B1.toInt(),
            0xFF039BE5.toInt(),
            0xFF43A047.toInt(),
            0xFFF4511E.toInt(),
        )
    }
}
