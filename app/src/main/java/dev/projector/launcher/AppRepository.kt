package dev.projector.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable

data class AppEntry(
    val component: ComponentName,
    val category: String,
    val label: String,
    val icon: Drawable,
    val banner: Drawable?,
    /** Vivid color pulled from the app's artwork; tints its card and the background glow. */
    val accent: Int,
) {
    val packageName: String get() = component.packageName

    /** Apps are keyed by package so favorites survive an app renaming its main activity. */
    val key: String get() = packageName
}

object AppRepository {

    const val DEFAULT_ACCENT = 0xFF3D5AFE.toInt()

    /**
     * Loads every launchable app. TV (leanback) entries win over phone-style entries
     * for the same package because they come with a proper 16:9 banner.
     */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val byPackage = LinkedHashMap<String, AppEntry>()

        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val activity = info.activityInfo
                if (activity.packageName == context.packageName) continue
                if (byPackage.containsKey(activity.packageName)) continue

                val banner = if (category == Intent.CATEGORY_LEANBACK_LAUNCHER) {
                    activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
                } else {
                    null
                }
                val icon = info.loadIcon(pm)
                byPackage[activity.packageName] = AppEntry(
                    component = ComponentName(activity.packageName, activity.name),
                    category = category,
                    label = info.loadLabel(pm).toString(),
                    icon = icon,
                    banner = banner,
                    accent = accentColor(banner ?: icon),
                )
            }
        }
        return byPackage.values.sortedBy { it.label.lowercase() }
    }

    /** Averages a tiny render of the artwork, weighting vivid pixels so logos beat backgrounds. */
    private fun accentColor(drawable: Drawable): Int {
        val size = 12
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            // Draw a copy so we don't disturb the bounds of the drawable the card will show.
            val copy = drawable.constantState?.newDrawable()?.mutate() ?: drawable
            copy.setBounds(0, 0, size, size)
            copy.draw(Canvas(bitmap))
        } catch (e: Exception) {
            bitmap.recycle()
            return DEFAULT_ACCENT
        }

        var red = 0L
        var green = 0L
        var blue = 0L
        var weight = 0L
        val hsv = FloatArray(3)
        for (x in 0 until size) {
            for (y in 0 until size) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) < 128) continue
                Color.colorToHSV(pixel, hsv)
                val w = 1 + (hsv[1] * hsv[2] * 12).toLong()
                red += Color.red(pixel) * w
                green += Color.green(pixel) * w
                blue += Color.blue(pixel) * w
                weight += w
            }
        }
        bitmap.recycle()
        if (weight == 0L) return DEFAULT_ACCENT

        Color.colorToHSV(Color.rgb((red / weight).toInt(), (green / weight).toInt(), (blue / weight).toInt()), hsv)
        // Black, white and grey artwork gives a muddy tint; use the default accent instead.
        if (hsv[1] < 0.15f) return DEFAULT_ACCENT
        hsv[1] = hsv[1].coerceAtLeast(0.55f)
        hsv[2] = 0.8f
        return Color.HSVToColor(hsv)
    }
}
